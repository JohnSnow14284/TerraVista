package dev.terravista.render.gpu;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.terravista.bootstrap.ModLog;
import dev.terravista.config.ClientOptions;
import dev.terravista.mesh.MeshBuildQueue;
import dev.terravista.model.ModelBakingService;
import dev.terravista.render.AtlasReadback;
import dev.terravista.render.CameraProjection;
import dev.terravista.render.DepthConvention;
import dev.terravista.render.ReadbackStream;
import dev.terravista.render.UploadStream;
import dev.terravista.render.VisibleBoundsBuffer;
import dev.terravista.render.tree.RenderRadius;
import dev.terravista.render.tree.TreePipeline;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.world.VoxelWorld;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** 持有世界渲染资源，连接宿主帧入口与构造、退出生命周期。 */
public final class VulkanFrames {
    private final VoxelWorld world;
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final VulkanReadbacks readbacks;
    private final DepthConvention convention;
    private final ModelBakingService models;
    private final MeshBuildQueue meshes;
    private final GpuGeometryTable geometry;
    private final TreePipeline tree;
    private final GpuVisibilityCleaner cleaner;
    private final LodTraversal traversal;
    private final TerrainDraws terrain;
    private final CompositePass composite;
    private final AmbientOcclusionPass occlusion;
    private final VisibleBoundsBuffer visible;
    private final BoundsPass bounds;
    private final VulkanViewTargets view;
    private final RenderRadius radius;
    private final FrameStages stages;
    private boolean closed;

    public VulkanFrames(VoxelWorld world, WeightedScheduler scheduler) {
        Deque<Runnable> cpu = new ArrayDeque<>();
        Deque<Runnable> gpu = new ArrayDeque<>();
        Deque<Runnable> singletons = new ArrayDeque<>();
        Runnable drain = null;
        world.acquireRef();
        try {
            ModLog.info("Render: Creating TerraVista pure-Vulkan render core");
            this.world = world;
            if (AdoptedDevice.current() == null) {
                throw new IllegalStateException("TerraVista Render: No Minecraft Vulkan host adapter registered");
            }
            commands = new FrameCommands(NativeRendererBackend.device());
            gpu.push(commands::close);
            drain = commands::waitIdleAndRetire;
            uploads = new VulkanUploads(commands, 1 << 26);
            gpu.push(uploads::free);
            readbacks = new VulkanReadbacks(commands, 1 << 25);
            gpu.push(readbacks::free);
            drain = readbacks::waitDiscard;
            UploadStream.register(uploads);
            singletons.push(UploadStream::clear);
            ReadbackStream.register(readbacks);
            singletons.push(ReadbackStream::clear);
            convention = DepthConvention.snapshot();
            AtlasReadback.register(new VulkanAtlasPixels(commands));
            singletons.push(AtlasReadback::clear);

            var modelTable = new GpuModelTable(commands, uploads);
            gpu.push(modelTable::free);
            models = new ModelBakingService(world.palette(), modelTable);
            gpu.pop();
            gpu.push(models::shutdown);
            meshes = new MeshBuildQueue(world, models, scheduler, false);
            cpu.push(meshes::shutdown);
            geometry = new GpuGeometryTable(commands, 1 << 20, 2048L << 20);
            gpu.push(geometry::close);
            var nodeUpdates = new GpuNodeUpdates(commands, uploads);
            gpu.push(nodeUpdates::close);
            tree = new TreePipeline(1 << 21, geometry, meshes, nodeUpdates);
            gpu.pop();
            cpu.push(tree::stop);
            cleaner = new GpuVisibilityCleaner(commands, uploads, readbacks, tree);
            gpu.push(cleaner::close);
            traversal = new LodTraversal(commands, uploads, readbacks, convention, tree, cleaner, meshes);
            gpu.push(traversal::close);
            terrain = new TerrainDraws(commands, uploads, readbacks, convention, geometry, modelTable);
            gpu.push(terrain::close);
            composite = new CompositePass(commands, uploads, convention, ClientOptions.CONFIG.fog().hasFog);
            gpu.push(composite::close);
            occlusion = new AmbientOcclusionPass(commands, uploads, convention, ClientOptions.CONFIG.ssao());
            gpu.push(occlusion::close);
            visible = new VisibleBoundsBuffer(size -> new GpuBuffer(commands, size));
            gpu.push(visible::close);
            bounds = new BoundsPass(commands, uploads, convention);
            gpu.push(bounds::close);

            cpu.push(() -> {
                world.primaryListener(null);
                world.palette().onBiome(null);
            });
            world.primaryListener(tree::worldChanged);
            Arrays.stream(world.palette().biomeEntries()).forEach(models::addBiome);
            world.palette().onBiome(models::addBiome);
            tree.start();
            view = new VulkanViewTargets(commands, convention, geometry.maxSections());
            gpu.push(view::close);
            int minSection = Minecraft.getInstance().level.getMinSectionY() >> 5;
            int maxSection = (Minecraft.getInstance().level.getMaxSectionY() - 1) >> 5;
            radius = new RenderRadius(minSection, maxSection, tree::addRoot, tree::removeRoot);
            distance(ClientOptions.CONFIG.renderDistanceChunks(Minecraft.getInstance().options.getEffectiveRenderDistance()) / 32f);
            stages = new FrameStages(composite, bounds, visible, terrain, readbacks, tree, cleaner,
                    traversal, occlusion, uploads, radius, models);
            commands.flushImmediate();
            ModLog.info("Render: TerraVista pure-Vulkan render core created with " + geometry.maxCapacity() + " geometry capacity");
        } catch (RuntimeException | Error failure) {
            rollback(cpu, failure);
            try {
                if (drain != null) drain.run();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            rollback(gpu, failure);
            rollback(singletons, failure);
            world.releaseRef();
            throw failure;
        }
    }

    private static void rollback(Deque<Runnable> actions, Throwable failure) {
        while (!actions.isEmpty()) {
            try {
                actions.pop().run();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
        }
    }

    /** 更新相机与附件，并在宿主命令缓冲内执行本帧。 */
    public void record(RenderTarget target, MinecraftDeviceAdapter adapter, ChunkRenderMatrices matrices,
                       double x, double y, double z) {
        var frameCommand = adapter.frameCommandBuffer();
        if (frameCommand == null) {
            ModLog.warn("Render: TerraVista VK: no frame command buffer at hook point, skipping frame");
            return;
        }
        var camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized) return;
        distance(ClientOptions.CONFIG.renderDistanceChunks(Minecraft.getInstance().options.getEffectiveRenderDistance()) / 32f);
        commands.flushImmediate();
        commands.beginFrame(frameCommand);
        try {
            var projection = CameraProjection.extend(convention, matrices.projection());
            var fog = camera.fogData == null ? null : new FogParameters(
                    camera.fogData.color.x, camera.fogData.color.y, camera.fogData.color.z, camera.fogData.color.w,
                    camera.fogData.environmentalStart, camera.fogData.environmentalEnd,
                    camera.fogData.renderDistanceStart, camera.fogData.renderDistanceEnd);
            view.state.sourceProjection(matrices.projection()).projection(projection)
                    .modelView(matrices.modelView()).camera(x, y, z).size(target.width, target.height)
                    .fog(fog).refresh();
            view.state.frame++;
            if (view.state.width <= 0 || view.state.height <= 0) return;
            view.ensureTargets();
            stages.record(new FrameAttachments(view, target.getColorTextureView(), target.getDepthTextureView(),
                    target.width, target.height));
        } finally {
            commands.endFrame();
            commands.retirement().poll();
        }
    }

    public void distance(float sections) { radius.radius((int) Math.ceil(sections + 1)); }

    public void debug(List<String> lines) {
        lines.add("TerraVista Vulkan host: " + NativeRendererBackend.status());
        lines.add("TerraVista GPU buffers [count/MB]: [" + GpuBuffer.count() + "/" + (GpuBuffer.totalBytes() / 1_000_000) + "]");
        models.addDebugData(lines);
        meshes.addDebugData(lines);
        tree.addDebug(lines);
        occlusion.debug(lines);
    }

    /** 先停止生产者，再按宿主设备存活状态释放渲染资源。 */
    public void close() {
        if (closed) return;
        closed = true;
        ModLog.info("Render: Shutting down TerraVista pure-Vulkan render core");
        try {
            world.primaryListener(null);
            world.palette().onBiome(null);
            world.palette().onBlock(null);
            tree.stop();
            meshes.shutdown();
        } catch (Exception failure) {
            ModLog.error("Render: Error stopping VK render core CPU services", failure);
        }
        if (AdoptedDevice.current() != null) {
            try {
                readbacks.waitDiscard();
                cleanup("model service", models::shutdown);
                cleanup("bound renderer", bounds::close);
                cleanup("visible section stream", visible::close);
                cleanup("traversal", traversal::close);
                cleanup("node cleaner", cleaner::close);
                cleanup("geometry data", geometry::close);
                cleanup("terrain renderer", terrain::close);
                cleanup("SSAO", occlusion::close);
                cleanup("compositor", composite::close);
                cleanup("view targets", view::close);
                cleanup("upload stream", uploads::free);
                cleanup("download stream", readbacks::free);
                cleanup("frame context", commands::close);
            } catch (Exception failure) {
                ModLog.error("Render: Error shutting down VK render core GPU resources", failure);
            }
        } else {
            ModLog.warn("Render: TerraVista VK: Minecraft's Vulkan device is already gone at shutdown; "
                    + "skipping GPU teardown to avoid destroying objects on a dead device");
        }
        UploadStream.clear();
        ReadbackStream.clear();
        AtlasReadback.clear();
        world.releaseRef();
        ModLog.info("Render: Vulkan renderer shutdown complete");
    }

    private static void cleanup(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception failure) {
            ModLog.error("Render: Error shutting down VK render core component: " + name, failure);
        }
    }

    public VisibleBoundsBuffer visibleBounds() { return visible; }
    public VoxelWorld world() { return world; }
}

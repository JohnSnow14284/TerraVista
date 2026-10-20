package dev.terravista.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.terravista.render.gpu.MinecraftDeviceAdapter;
import dev.terravista.render.gpu.VulkanFrames;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.world.VoxelWorld;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import java.util.List;

/** 连接世界渲染会话、可见区块采集和宿主帧调用。 */
public final class RenderSession {
    private final VoxelWorld world;
    private final VulkanFrames frames;
    private final VisibleBoundsBuffer visible;

    public RenderSession(VoxelWorld world, WeightedScheduler scheduler) {
        if (!GraphicsMode.nativeVulkan()) {
            throw new IllegalStateException("TerraVista requires Minecraft's Vulkan backend");
        }
        this.world = world;
        frames = new VulkanFrames(world, scheduler);
        visible = frames.visibleBounds();
    }

    public void record(RenderTarget target, MinecraftDeviceAdapter adapter, ChunkRenderMatrices matrices,
                       double x, double y, double z) {
        frames.record(target, adapter, matrices, x, y, z);
    }

    public boolean hasFrameResources() { return frames != null; }

    public void distance(float sections) { frames.distance(sections); }
    public void debug(List<String> lines) { frames.debug(lines); }
    public void close() { frames.close(); }
    public VoxelWorld world() { return world; }
    public VisibleBoundsBuffer visibleBounds() { return visible; }
}

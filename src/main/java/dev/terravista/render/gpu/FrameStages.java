package dev.terravista.render.gpu;

import dev.terravista.model.ModelBakingService;
import dev.terravista.config.ClientOptions;
import dev.terravista.render.VisibleBoundsBuffer;
import dev.terravista.render.tree.RenderRadius;
import dev.terravista.render.tree.TreePipeline;

/** 按帧顺序连接远景绘制、树更新和帧末CPU工作。 */
final class FrameStages {
    private final CompositePass composite;
    private final BoundsPass bounds;
    private final VisibleBoundsBuffer visible;
    private final TerrainDraws terrain;
    private final VulkanReadbacks readbacks;
    private final TreePipeline tree;
    private final GpuVisibilityCleaner cleaner;
    private final LodTraversal traversal;
    private final AmbientOcclusionPass occlusion;
    private final VulkanUploads uploads;
    private final RenderRadius radius;
    private final ModelBakingService models;

    FrameStages(CompositePass composite, BoundsPass bounds, VisibleBoundsBuffer visible, TerrainDraws terrain,
                VulkanReadbacks readbacks, TreePipeline tree, GpuVisibilityCleaner cleaner, LodTraversal traversal,
                AmbientOcclusionPass occlusion, VulkanUploads uploads, RenderRadius radius, ModelBakingService models) {
        this.composite = composite;
        this.bounds = bounds;
        this.visible = visible;
        this.terrain = terrain;
        this.readbacks = readbacks;
        this.tree = tree;
        this.cleaner = cleaner;
        this.traversal = traversal;
        this.occlusion = occlusion;
        this.uploads = uploads;
        this.radius = radius;
        this.models = models;
    }

    void record(FrameAttachments frame) {
        var view = frame.view();
        composite.initialize(frame);
        bounds.record(view, visible);
        terrain.publish(view);
        terrain.opaque(view, false);
        DepthSetupPass.sampled(view);
        view.depthPyramid.build(view.attachments.depthSampleView, view.state.width, view.state.height);
        DepthSetupPass.attachment(view);
        readbacks.tick();
        tree.tick(traversal.nodes(), cleaner);
        cleaner.processFrame(traversal.nodes());
        traversal.record(view);
        terrain.build(view);
        if (!CompositePass.hiddenByFog(view)) {
            terrain.temporal(view);
            occlusion.record(view, frame.depth());
            terrain.translucent(view);
            DepthSetupPass.sampled(view);
            composite.record(frame);
        }
        uploads.tick();
        var pressure = ClientOptions.CONFIG.pressure();
        radius.process(view.state.cameraX, view.state.cameraZ, pressure.rootColumns);
        models.tick(pressure.modelNanos);
    }
}

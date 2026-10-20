package dev.terravista.render.gpu;

import dev.terravista.config.ClientOptions;
import dev.terravista.mesh.MeshBuildQueue;
import net.minecraft.client.Minecraft;
import static org.lwjgl.system.MemoryUtil.*;

/** 写入遍历和曲率参数，等待统一提交上传。 */
final class TraversalScene {
    static void upload(VulkanUploads uploads, TraversalQueues queues, VulkanViewTargets targets,
                       GpuVisibilityCleaner cleaner, MeshBuildQueue meshes) {
        long pointer = uploads.upload(queues.uniform, 0, queues.uniform.size());
        var view = targets.state;
        view.transform.getToAddress(pointer); pointer += 64;
        view.section.getToAddress(pointer); pointer += 12;
        memPutInt(pointer, targets.depthPyramid.packedSize()); pointer += 4;
        view.offset.getToAddress(pointer); pointer += 12;
        final float pixels = ClientOptions.CONFIG.detailPixels();
        final float subdivision = pixels * pixels;
        memPutFloat(pointer, subdivision / (view.width * view.height)); pointer += 4;
        for (int i = 0; i < 6; i++) { view.planes[i].getToAddress(pointer); pointer += 16; }
        memPutInt(pointer, (int) (targets.lookup.size() / 4 - 1)); pointer += 4;
        memPutInt(pointer, cleaner.epoch); pointer += 4;
        var pressure = ClientOptions.CONFIG.pressure();
        final double target = pressure.backlog;
        double available = Math.max(0, (target - meshes.taskCount()) / target);
        available = Math.pow(available, 2);
        final int budget = (int) Math.ceil(available * pressure.requests);
        memPutInt(pointer, Math.max(0, Math.min(TraversalQueues.REQUEST_CAPACITY, budget))); pointer += 4;
        final float blocks = ClientOptions.CONFIG.renderDistanceChunks(
                Minecraft.getInstance().options.getEffectiveRenderDistance()) * 16f;
        memPutFloat(pointer, blocks * blocks); pointer += 4;
        float curve = dev.terravista.render.WorldCurvature.coefficient();
        memPutFloat(pointer, curve); pointer += 4;
        memPutFloat(pointer, 0f); pointer += 4;
        memPutLong(pointer, 0);
    }

    private TraversalScene() {}
}

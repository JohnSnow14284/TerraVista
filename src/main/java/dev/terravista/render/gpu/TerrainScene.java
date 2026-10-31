package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import dev.terravista.compat.VitrailBuffers;
import dev.terravista.compat.VitrailDepth;
import dev.terravista.compat.VitrailPackedPrograms;
import static org.lwjgl.system.MemoryUtil.*;

/** 持有当前地形参数切片，在上传切换时保留对应暂存区。 */
final class TerrainScene {
    private static final int BYTES = 112;
    private final Matrix4f translated = new Matrix4f();
    private final Vector2f depthPair = new Vector2f();
    private GpuBuffer owner;
    private long buffer, offset;

    void publish(VulkanUploads uploads, VulkanViewTargets targets) {
        offset = uploads.rawUploadAddress(BYTES);
        owner = uploads.stagingOwner();
        buffer = uploads.stagingBuffer();
        long pointer = uploads.baseAddress() + offset;
        var view = targets.state;
        translated.set(view.transform).translate(-view.offset.x, -view.offset.y, -view.offset.z).getToAddress(pointer);
        view.section.getToAddress(pointer + 64);
        if (view.frame < 0) {
            ModLog.error("Render: Frame ID negative, this will cause things to break, wrapping around");
            view.frame &= 0x7fffffff;
        }
        memPutInt(pointer + 76, view.frame & 0x7fffffff);
        view.offset.getToAddress(pointer + 80);
        memPutFloat(pointer + 92, Minecraft.getInstance().level.getSeaLevel() - 1f / 9f - 0.001f);
        float curve = dev.terravista.render.WorldCurvature.coefficient();
        memPutFloat(pointer + 96, curve);
        memPutFloat(pointer + 100, 0f);
        if (VitrailPackedPrograms.ENABLED) {
            var worldProjection = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix;
            VitrailDepth.worldPair(worldProjection, VitrailDepth.row(depthPair));
            memPutFloat(pointer + 104, depthPair.x);
            memPutFloat(pointer + 108, depthPair.y);
        } else memPutLong(pointer + 104, 0);
        uploads.commit();
    }

    DescriptorWrites bind(DescriptorWrites descriptors) {
        return descriptors.buffer(0, PipelineBindings.UNIFORM_BUFFER, buffer, offset, BYTES);
    }

    void ensureLive(VulkanUploads uploads, VulkanViewTargets view) {
        // Tree/model uploads can rotate the staging bank between publication and either pack pass.
        // Earlier GPU reads retire normally; the next borrowed facade needs the current live owner.
        if (owner.isReleased()) publish(uploads, view);
    }

    void bind(VitrailBuffers buffers) { buffers.set(VitrailBuffers.Slot.SCENE, owner, offset, BYTES); }
}

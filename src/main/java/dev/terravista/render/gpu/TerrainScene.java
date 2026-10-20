package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import static org.lwjgl.system.MemoryUtil.*;

/** 持有当前地形参数切片，在上传切换时保留对应暂存区。 */
final class TerrainScene {
    private static final int BYTES = 112;
    private final Matrix4f translated = new Matrix4f();
    private long buffer, offset;

    void publish(VulkanUploads uploads, VulkanViewTargets targets) {
        offset = uploads.rawUploadAddress(BYTES);
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
        memPutLong(pointer + 104, 0);
        uploads.commit();
    }

    DescriptorWrites bind(DescriptorWrites descriptors) {
        return descriptors.buffer(0, PipelineBindings.UNIFORM_BUFFER, buffer, offset, BYTES);
    }
}

package dev.terravista.render.gpu;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import static org.lwjgl.system.MemoryUtil.*;

/** 打包可见体积相机参数并跨帧保留暂存数据。 */
final class BoundsScene {
    private final Vector3i block = new Vector3i();
    private final Vector3f fraction = new Vector3f();
    private final Matrix4f translated = new Matrix4f();

    void publish(VulkanUploads uploads, GpuBuffer uniform, VulkanViewTargets targets) {
        final float distance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        long pointer = uploads.upload(uniform, 0, uniform.sizeBytes());
        var view = targets.state;
        int x = (int) Math.floor(view.cameraX);
        int y = (int) Math.floor(view.cameraY);
        int z = (int) Math.floor(view.cameraZ);
        block.set(x, y, z).getToAddress(pointer + 64);
        memPutInt(pointer + 76, 0);
        fraction.set((float) (view.cameraX - x), (float) (view.cameraY - y), (float) (view.cameraZ - z));
        fraction.getToAddress(pointer + 80);
        view.transform.translate(fraction.negate(), translated).getToAddress(pointer);
        memPutFloat(pointer + 92, distance);
        uploads.commit();
    }
}

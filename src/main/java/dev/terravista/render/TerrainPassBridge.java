package dev.terravista.render;

import dev.terravista.compat.RenderSessionAccess;
import dev.terravista.render.gpu.AdoptedDevice;
import dev.terravista.render.gpu.MinecraftDeviceAdapter;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;

/** 保留当前地形帧参数并在宿主通道结束后提交远景。 */
public final class TerrainPassBridge {
    private static ChunkRenderMatrices capturedMatrices;
    private static CameraTransform origin;
    private static boolean submitted;

    public static void capture(ChunkRenderMatrices matrices, double x, double y, double z) {
        capturedMatrices = matrices;
        origin = new CameraTransform(x, y, z);
        submitted = false;
    }

    public static boolean requiresOwnPass() {
        return capturedMatrices != null && GraphicsMode.nativeVulkan();
    }

    public static void submitOpaque() {
        if (capturedMatrices == null || submitted) return;
        submitted = true;
        var renderer = Minecraft.getInstance().gameRenderer;
        var target = renderer.mainRenderTarget();
        if (GraphicsMode.nativeVulkan() && AdoptedDevice.current() instanceof MinecraftDeviceAdapter adapter) {
            var session = RenderSessionAccess.current();
            if (session != null && session.hasFrameResources()) {
                session.record(target, adapter, capturedMatrices, origin.x, origin.y, origin.z);
            }
        }
    }

    public static void finishTranslucent() {
        capturedMatrices = null;
        origin = null;
        submitted = false;
    }

    private TerrainPassBridge() {}
}

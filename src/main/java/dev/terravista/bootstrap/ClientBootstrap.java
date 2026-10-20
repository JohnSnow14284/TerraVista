package dev.terravista.bootstrap;

import com.mojang.renderpearl.api.device.GpuDevice;
import dev.terravista.render.GraphicsMode;
import dev.terravista.render.gpu.NativeRendererBackend;
import dev.terravista.world.ClientWorldSession;
import net.minecraft.client.Minecraft;

/** 在宿主图形设备可用后连接客户端会话工厂。 */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void initialize(GpuDevice device) {
        GraphicsMode.initialize(device);
        if (GraphicsMode.current() != GraphicsMode.VULKAN) {
            ModLog.warn("Startup: TerraVista requires Minecraft's Vulkan graphics backend; TerraVista is disabled.");
            return;
        }
        if (!NativeRendererBackend.available()) {
            ModLog.error("Startup: TerraVista could not adopt Minecraft's Vulkan device: " + NativeRendererBackend.status());
            return;
        }
        if (InstanceFileLock.requested()
                && !InstanceFileLock.acquire(Minecraft.getInstance().gameDirectory.toPath())) return;
        GraphicsMode.enableVulkan();
        SessionLifecycle.factory(ClientWorldSession::open);
        ModLog.info("Startup: TerraVista initialised on Minecraft's Vulkan backend: " + NativeRendererBackend.status());
    }
}

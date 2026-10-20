package dev.terravista.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;

/** 发布借用的宿主设备并查询回退选择后的当前后端。 */
public final class AdoptedDevice {
    private static volatile MinecraftDeviceAccess host;

    public static void register(MinecraftDeviceAccess device) { host = device; }
    public static void clear() { host = null; }
    public static MinecraftDeviceAccess current() { return host; }
    public static boolean activeVulkan() {
        if (host != null) return true;
        return detectBackend();
    }
    private static boolean detectBackend() {
        try {
            var device = RenderSystem.tryGetDevice();
            if (device == null) return false;
            String backend = device.getDeviceInfo().backendName();
            return backend != null && backend.toLowerCase(Locale.ROOT).contains("vulkan");
        } catch (Throwable failure) {
            return false;
        }
    }
    private AdoptedDevice() {}
}

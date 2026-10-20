package dev.terravista.render;

import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.Locale;

/** 跟踪 Minecraft 选择的图形后端与原生渲染器激活状态。 */
public enum GraphicsMode {
    UNKNOWN, VULKAN;

    private static GraphicsMode current = UNKNOWN;
    private static boolean available;

    public static void initialize(GpuDevice device) {
        current = device.getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan") ? VULKAN : UNKNOWN;
        available = false;
    }
    public static GraphicsMode current() { return current; }
    public static void enableVulkan() {
        if (current != VULKAN) throw new IllegalStateException("TerraVista Render: Minecraft is not using Vulkan");
        available = true;
    }
    public static boolean nativeVulkan() { return current == VULKAN && available; }
    public static String statusLine() { return "minecraft=" + current.name().toLowerCase(Locale.ROOT) + ", vulkanAvailable=" + available; }
}

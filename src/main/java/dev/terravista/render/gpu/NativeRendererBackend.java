package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;

/** 缓存宿主设备采纳结果并管理后端资源生命周期。 */
public final class NativeRendererBackend {
    private enum Probe { UNPROBED, READY, UNAVAILABLE }
    private static Probe probe = Probe.UNPROBED;
    private static VulkanDevice device;
    private static String reason = "not probed";

    public static boolean available() {
        if (!AdoptedDevice.activeVulkan()) return false;
        if (AdoptedDevice.current() == null) {
            ModLog.info("Render: TerraVista: Minecraft on Vulkan but host adapter not yet registered");
            return false;
        }
        return supported();
    }

    public static synchronized boolean supported() {
        if (probe == Probe.UNPROBED) {
            try {
                Class.forName("org.lwjgl.vulkan.VK10");
                var host = AdoptedDevice.current();
                if (host == null) {
                    probe = Probe.UNAVAILABLE;
                    reason = "no Minecraft Vulkan host adapter";
                } else {
                    device = VulkanDevice.adopt(host);
                    probe = Probe.READY;
                    reason = null;
                    ModLog.info("Render: TerraVista Vulkan backend adopting Minecraft's device: " + device.name);
                }
            } catch (Throwable failure) {
                probe = Probe.UNAVAILABLE;
                reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                ModLog.info("Render: TerraVista Vulkan backend unavailable: " + reason);
            }
        }
        return probe == Probe.READY;
    }

    public static synchronized VulkanDevice device() {
        if (!supported()) throw new IllegalStateException("TerraVista Render: Vulkan not supported: " + reason);
        return device;
    }

    public static String status() {
        return switch (probe) {
            case UNPROBED -> "vk: unprobed";
            case READY -> "vk: host(" + device.name + ")";
            case UNAVAILABLE -> "vk: unavailable (" + reason + ")";
        };
    }

    public static synchronized void close() {
        if (device != null) {
            device.close();
            device = null;
        }
        probe = Probe.UNPROBED;
    }

    private NativeRendererBackend() {}
}

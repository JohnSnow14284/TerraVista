package dev.terravista.render.gpu;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import dev.terravista.bootstrap.ModLog;

/** 连接宿主设备的注册、后端释放和适配器清除。 */
public final class DeviceAttachment {
    public static void attach(com.mojang.renderpearl.backend.vulkan.VulkanDevice device, FeatureSet features) {
        try {
            boolean indirectCount = features.features().contains(
                    new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "drawIndirectCount"));
            AdoptedDevice.register(new MinecraftDeviceAdapter(device, indirectCount));
            ModLog.info("Render: TerraVista: adopted Minecraft's Vulkan device (pure-VK host mode available)");
        } catch (Throwable failure) {
            ModLog.warn("Render: TerraVista: failed to register Vulkan host adapter, TerraVista will be inactive: " + failure);
            AdoptedDevice.clear();
        }
    }

    public static void detach() {
        try {
            NativeRendererBackend.close();
        } finally {
            AdoptedDevice.clear();
        }
    }

    private DeviceAttachment() {}
}

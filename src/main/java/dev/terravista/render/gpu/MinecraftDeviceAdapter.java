package dev.terravista.render.gpu;

import dev.terravista.mixin.vulkan.RecordingCommandAccess;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

/** 借用宿主设备并在每次查询时读取持久编码器状态。 */
public final class MinecraftDeviceAdapter implements MinecraftDeviceAccess {
    private final com.mojang.renderpearl.backend.vulkan.VulkanDevice host;
    private final boolean indirectCount;

    public MinecraftDeviceAdapter(com.mojang.renderpearl.backend.vulkan.VulkanDevice host, boolean indirectCount) {
        this.host = host;
        this.indirectCount = indirectCount;
    }

    @Override public boolean drawIndirectCountEnabled() { return indirectCount; }
    @Override public VkInstance instance() { return host.instance().vkInstance(); }
    @Override public VkPhysicalDevice physicalDevice() { return host.vkDevice().getPhysicalDevice(); }
    @Override public VkDevice device() { return host.vkDevice(); }
    @Override public VkQueue graphicsQueue() { return host.graphicsQueue().vkQueue(); }
    @Override public int graphicsQueueFamily() { return host.graphicsQueue().queueFamilyIndex(); }
    @Override public VkCommandBuffer frameCommandBuffer() {
        var access = (RecordingCommandAccess) (Object) host.createCommandEncoder();
        return access.terravista$recordingCommand();
    }
}

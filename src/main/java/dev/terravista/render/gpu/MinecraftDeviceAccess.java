package dev.terravista.render.gpu;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

/** 借用 Minecraft 设备句柄并查询当前帧命令缓冲。 */
public interface MinecraftDeviceAccess {
    VkInstance instance();
    VkPhysicalDevice physicalDevice();
    VkDevice device();
    VkQueue graphicsQueue();
    int graphicsQueueFamily();
    boolean drawIndirectCountEnabled();
    VkCommandBuffer frameCommandBuffer();
}

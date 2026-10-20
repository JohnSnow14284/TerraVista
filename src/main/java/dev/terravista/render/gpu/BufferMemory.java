package dev.terravista.render.gpu;

import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 分配一致性暂存内存，并按需优先选择缓存读回内存。 */
final class BufferMemory {
    static long allocate(VulkanDevice device, VkMemoryRequirements requirements, int usage, boolean hostVisible) {
        int required = hostVisible
                ? VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                : VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
        int preferred = hostVisible && usage == VK_BUFFER_USAGE_TRANSFER_DST_BIT
                ? VK_MEMORY_PROPERTY_HOST_CACHED_BIT : 0;
        try (var stack = stackPush()) {
            var allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(device.memory.find(requirements.memoryTypeBits(), required, preferred));
            var handle = stack.mallocLong(1);
            int result = vkAllocateMemory(device.device, allocate, null, handle);
            if (preferred != 0 && (result == VK_ERROR_OUT_OF_DEVICE_MEMORY || result == VK_ERROR_OUT_OF_HOST_MEMORY)) {
                int fallback = device.memory.find(requirements.memoryTypeBits(), required);
                if (fallback != allocate.memoryTypeIndex()) {
                    allocate.memoryTypeIndex(fallback);
                    result = vkAllocateMemory(device.device, allocate, null, handle);
                }
            }
            check(result, "vkAllocateMemory");
            return handle.get(0);
        }
    }
}

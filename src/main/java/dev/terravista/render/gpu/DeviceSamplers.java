package dev.terravista.render.gpu;

import org.lwjgl.vulkan.VkSamplerCreateInfo;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 在借用设备生命周期内缓存四类采样器并统一释放。 */
public final class DeviceSamplers {
    public static long get(VulkanDevice device, boolean mipmapNearest, boolean linear) {
        int key = (mipmapNearest ? 1 : 0) | (linear ? 2 : 0);
        Long cached = device.samplers.get(key);
        if (cached != null) return cached;
        try (var stack = stackPush()) {
            var create = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST)
                    .minFilter(linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).minLod(0).maxLod(mipmapNearest ? VK_LOD_CLAMP_NONE : 0.25f);
            var handle = stack.mallocLong(1);
            check(vkCreateSampler(device.device, create, null, handle), "vkCreateSampler");
            long result = handle.get(0);
            device.samplers.put(key, result);
            return result;
        }
    }
}

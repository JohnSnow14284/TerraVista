package dev.terravista.render.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;

import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.vulkan.KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

/** 缓存设备持有的描述符表并创建调用方持有的推送常量布局。 */
final class PipelineLayouts {
    static long descriptors(VulkanDevice device, MemoryStack stack, List<PipelineBindings.Entry> entries, int stages) {
        var key = PipelineBindings.key(entries, stages);
        Long cached = device.descriptorLayouts.get(key);
        if (cached != null) return cached;

        var bindings = VkDescriptorSetLayoutBinding.calloc(entries.size(), stack);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            bindings.get(i).binding(entry.binding()).descriptorType(entry.type()).descriptorCount(1).stageFlags(stages);
        }
        var create = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR).pBindings(bindings);
        var handle = stack.mallocLong(1);
        check(vkCreateDescriptorSetLayout(device.device, create, null, handle), "vkCreateDescriptorSetLayout");
        long layout = handle.get(0);
        device.descriptorLayouts.put(key, layout);
        return layout;
    }

    static long pipeline(VulkanDevice device, MemoryStack stack, long descriptors, int pushBytes, int stages) {
        var create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(descriptors));
        if (pushBytes > 0) {
            var range = VkPushConstantRange.calloc(1, stack).stageFlags(stages).offset(0).size(pushBytes);
            create.pPushConstantRanges(range);
        }
        var handle = stack.mallocLong(1);
        check(vkCreatePipelineLayout(device.device, create, null, handle), "vkCreatePipelineLayout");
        return handle.get(0);
    }

    private PipelineLayouts() {}
}

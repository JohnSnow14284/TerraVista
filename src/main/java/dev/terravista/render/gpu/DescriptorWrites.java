package dev.terravista.render.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRPushDescriptor.vkCmdPushDescriptorSetKHR;
import static org.lwjgl.vulkan.VK10.*;

/** 在渲染线程栈中准备描述符写入并推送到零号集合。 */
public final class DescriptorWrites implements AutoCloseable {
    private final DevicePipeline owner;
    private final MemoryStack stack = stackPush();
    private final VkWriteDescriptorSet.Buffer writes;
    private int count;

    DescriptorWrites(DevicePipeline owner) {
        this.owner = owner;
        writes = VkWriteDescriptorSet.calloc(owner.bindingCount, stack);
    }

    private VkWriteDescriptorSet next() {
        writes.limit(writes.capacity());
        return writes.get(count++).sType$Default().descriptorCount(1);
    }

    public DescriptorWrites buffer(int binding, int type, long buffer, long offset, long range) {
        var info = VkDescriptorBufferInfo.calloc(1, stack).buffer(buffer).offset(offset).range(range);
        next().dstBinding(binding).descriptorType(type).pBufferInfo(info);
        return this;
    }

    public DescriptorWrites uniform(int binding, GpuBuffer buffer) { return buffer(binding, PipelineBindings.UNIFORM_BUFFER, buffer.buffer, 0, buffer.size()); }
    public DescriptorWrites storage(int binding, GpuBuffer buffer) { return buffer(binding, PipelineBindings.STORAGE_BUFFER, buffer.buffer, 0, buffer.size()); }
    public DescriptorWrites storage(int binding, long buffer, long offset, long range) { return buffer(binding, PipelineBindings.STORAGE_BUFFER, buffer, offset, range); }

    public DescriptorWrites sampler(int binding, long view, long sampler, int layout) {
        var info = VkDescriptorImageInfo.calloc(1, stack).sampler(sampler).imageView(view).imageLayout(layout);
        next().dstBinding(binding).descriptorType(PipelineBindings.COMBINED_SAMPLER).pImageInfo(info);
        return this;
    }

    public DescriptorWrites image(int binding, long view) {
        var info = VkDescriptorImageInfo.calloc(1, stack).imageView(view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        next().dstBinding(binding).descriptorType(PipelineBindings.STORAGE_IMAGE).pImageInfo(info);
        return this;
    }

    public void push(VkCommandBuffer command) {
        writes.limit(count);
        vkCmdPushDescriptorSetKHR(command, owner.compute ? VK_PIPELINE_BIND_POINT_COMPUTE : VK_PIPELINE_BIND_POINT_GRAPHICS,
                owner.pipelineLayout, 0, writes);
    }

    @Override public void close() { stack.close(); }
}

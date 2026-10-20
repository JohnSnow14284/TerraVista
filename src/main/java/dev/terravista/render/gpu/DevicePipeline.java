package dev.terravista.render.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.ByteBuffer;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.vulkan.VK10.*;

/** 持有编译管线与私有布局，等待帧完成后释放。 */
public final class DevicePipeline {
    private final FrameCommands commands;
    public final long descriptorSetLayout;
    public final long pipelineLayout;
    public final long pipeline;
    final boolean compute;
    final int pushStages;
    final int bindingCount;

    DevicePipeline(FrameCommands commands, long descriptors, long layout, long pipeline, boolean compute, int stages, int bindingCount) {
        this.commands = commands;
        this.descriptorSetLayout = descriptors;
        this.pipelineLayout = layout;
        this.pipeline = pipeline;
        this.compute = compute;
        this.pushStages = stages;
        this.bindingCount = bindingCount;
    }

    static long module(VulkanDevice device, ByteBuffer binary, MemoryStack stack) {
        var create = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(binary);
        var handle = stack.mallocLong(1);
        check(vkCreateShaderModule(device.device, create, null, handle), "vkCreateShaderModule");
        return handle.get(0);
    }

    public void bind(VkCommandBuffer command) {
        vkCmdBindPipeline(command, compute ? VK_PIPELINE_BIND_POINT_COMPUTE : VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    public void pushConstants(VkCommandBuffer command, ByteBuffer data) {
        vkCmdPushConstants(command, pipelineLayout, pushStages, 0, data);
    }

    public DescriptorWrites descriptors() { return new DescriptorWrites(this); }

    public void free() { commands.retirement().deferPipeline(pipeline, pipelineLayout); }
}

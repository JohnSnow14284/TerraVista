package dev.terravista.render.gpu;

import org.lwjgl.vulkan.*;
import java.util.List;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 创建计算管线并同步释放临时模块与失败的部分结果。 */
public final class ComputePipeline {
    public static DevicePipeline create(FrameCommands commands, String name, String source, int pushBytes, List<PipelineBindings.Entry> bindings) {
        int count = bindings.size();
        var device = commands.device();
        long module = 0, layout = 0, pipeline = 0;
        try (var stack = stackPush()) {
            module = DevicePipeline.module(device, SpirvCompiler.compile(source, ShaderStage.COMPUTE, name), stack);
            long descriptors = PipelineLayouts.descriptors(device, stack, bindings, VK_SHADER_STAGE_COMPUTE_BIT);
            layout = PipelineLayouts.pipeline(device, stack, descriptors, pushBytes, VK_SHADER_STAGE_COMPUTE_BIT);
            var create = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default().layout(layout);
            create.stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            var handle = stack.callocLong(1);
            int result = vkCreateComputePipelines(device.device, device.pipelineCache(), create, null, handle);
            pipeline = handle.get(0);
            check(result, "vkCreateComputePipelines(" + name + ")");
            return new DevicePipeline(commands, descriptors, layout, pipeline, true, VK_SHADER_STAGE_COMPUTE_BIT, count);
        } catch (RuntimeException | Error failure) {
            if (pipeline != 0) vkDestroyPipeline(device.device, pipeline, null);
            if (layout != 0) vkDestroyPipelineLayout(device.device, layout, null);
            throw failure;
        } finally {
            if (module != 0) vkDestroyShaderModule(device.device, module, null);
        }
    }

    private ComputePipeline() {}
}

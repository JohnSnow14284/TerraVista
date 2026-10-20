package dev.terravista.render.gpu;

import org.lwjgl.vulkan.*;
import java.util.ArrayList;
import java.util.List;
import static dev.terravista.render.gpu.VulkanCalls.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 创建动态渲染图形状态，另行管理共享对象与描述符暂存。 */
public final class GraphicsPipeline {
    public static final class Description {
        public String name;
        public String vertexSource, fragmentSource;
        public int pushBytes;
        public List<PipelineBindings.Entry> bindings = new ArrayList<>();
        public int colorFormat, depthFormat, stencilFormat;
        public boolean depthTest = true, depthWrite = true;
        public int depthCompare = VK_COMPARE_OP_LESS_OR_EQUAL;
        public boolean blend;
        public boolean colorWrite = true;
        public boolean stencilTestEqual1, stencilWriteAlways1;
        public int stencilWriteRef = 1;
        public int topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    }

    private static boolean strip(int topology) {
        return topology == VK_PRIMITIVE_TOPOLOGY_LINE_STRIP
                || topology == VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
                || topology == VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN
                || topology == VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY
                || topology == VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY;
    }

    public static DevicePipeline create(FrameCommands commands, Description description) {
        int count = description.bindings.size();
        int pushStages = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        var device = commands.device();
        long vertex = 0, fragment = 0, layout = 0, pipeline = 0;
        try (var stack = stackPush()) {
            vertex = DevicePipeline.module(device, SpirvCompiler.compile(description.vertexSource, ShaderStage.VERTEX, description.name + ".vert"), stack);
            fragment = DevicePipeline.module(device, SpirvCompiler.compile(description.fragmentSource, ShaderStage.FRAGMENT, description.name + ".frag"), stack);
            long descriptors = PipelineLayouts.descriptors(device, stack, description.bindings, pushStages);
            layout = PipelineLayouts.pipeline(device, stack, descriptors, description.pushBytes, pushStages);

            var stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertex).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragment).pName(stack.UTF8("main"));
            var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            // 为 MoltenVK 的条带和扇形启用重启，列表拓扑保持关闭。
            var assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default()
                    .topology(description.topology).primitiveRestartEnable(strip(description.topology));
            var dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            var viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            var raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1);
            var samples = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            var depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                    .depthTestEnable(description.depthTest).depthWriteEnable(description.depthWrite).depthCompareOp(description.depthCompare);
            if (description.stencilTestEqual1 || description.stencilWriteAlways1) {
                depth.stencilTestEnable(true);
                var front = depth.front();
                if (description.stencilWriteAlways1) {
                    front.failOp(VK_STENCIL_OP_KEEP).passOp(VK_STENCIL_OP_REPLACE).depthFailOp(VK_STENCIL_OP_KEEP)
                            .compareOp(VK_COMPARE_OP_ALWAYS).compareMask(0xFF).writeMask(0xFF).reference(description.stencilWriteRef);
                } else {
                    front.failOp(VK_STENCIL_OP_KEEP).passOp(VK_STENCIL_OP_KEEP).depthFailOp(VK_STENCIL_OP_KEEP)
                            .compareOp(VK_COMPARE_OP_EQUAL).compareMask(0xFF).writeMask(0x00).reference(1);
                }
                depth.back(depth.front());
            }
            var attachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(description.colorWrite ? 0xF : 0).blendEnable(description.blend);
            if (description.blend) {
                attachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK_BLEND_OP_ADD).srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).alphaBlendOp(VK_BLEND_OP_ADD);
            }
            var blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default();
            if (description.colorFormat != VK_FORMAT_UNDEFINED) blend.pAttachments(attachment);
            var rendering = VkPipelineRenderingCreateInfoKHR.calloc(stack).sType$Default()
                    .depthAttachmentFormat(description.depthFormat).stencilAttachmentFormat(description.stencilFormat);
            if (description.colorFormat != VK_FORMAT_UNDEFINED) rendering.colorAttachmentCount(1).pColorAttachmentFormats(stack.ints(description.colorFormat));
            var create = VkGraphicsPipelineCreateInfo.calloc(1, stack).sType$Default().pNext(rendering).pStages(stages)
                    .pVertexInputState(vertexInput).pInputAssemblyState(assembly).pViewportState(viewport)
                    .pRasterizationState(raster).pMultisampleState(samples).pDepthStencilState(depth)
                    .pColorBlendState(blend).pDynamicState(dynamic).layout(layout);
            var handle = stack.callocLong(1);
            int result = vkCreateGraphicsPipelines(device.device, device.pipelineCache(), create, null, handle);
            pipeline = handle.get(0);
            check(result, "vkCreateGraphicsPipelines(" + description.name + ")");
            return new DevicePipeline(commands, descriptors, layout, pipeline, false, pushStages, count);
        } catch (RuntimeException | Error failure) {
            if (pipeline != 0) vkDestroyPipeline(device.device, pipeline, null);
            if (layout != 0) vkDestroyPipelineLayout(device.device, layout, null);
            throw failure;
        } finally {
            if (fragment != 0) vkDestroyShaderModule(device.device, fragment, null);
            if (vertex != 0) vkDestroyShaderModule(device.device, vertex, null);
        }
    }

    private GraphicsPipeline() {}
}

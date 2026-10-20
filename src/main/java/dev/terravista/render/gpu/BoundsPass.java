package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import dev.terravista.render.PrimitiveIndices;
import dev.terravista.render.VisibleBoundsBuffer;
import java.util.List;
import org.lwjgl.vulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;

/** 绘制 Sodium 可见区段的远端深度边界。 */
public final class BoundsPass {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final DepthConvention convention;
    private final GpuBuffer uniform, indices;
    private final DevicePipeline pipeline;
    private final BoundsScene scene = new BoundsScene();

    public BoundsPass(FrameCommands commands, VulkanUploads uploads, DepthConvention convention) {
        this.commands = commands;
        this.uploads = uploads;
        this.convention = convention;
        GpuBuffer createdUniform = null, createdIndices = null;
        try {
            uniform = createdUniform = new GpuBuffer(commands, 96);
            uniform.zero();
            indices = createdIndices = new GpuBuffer(commands, 72);
            PrimitiveIndices.writeCube16(uploads.upload(indices, 0, indices.sizeBytes()));
            uploads.commit();
            pipeline = createPipeline(commands, convention);
        } catch (RuntimeException | Error failure) {
            if (createdIndices != null) createdIndices.free();
            if (createdUniform != null) createdUniform.free();
            throw failure;
        }
    }

    private static DevicePipeline createPipeline(FrameCommands commands, DepthConvention convention) {
        var description = new GraphicsPipeline.Description();
        description.name = "chunk-bounds";
        description.vertexSource = ShaderVariants.load(ShaderProgram.BOUNDS_VERTEX, ShaderVariants.defines().depth(convention).build());
        description.fragmentSource = ShaderVariants.load(ShaderProgram.BOUNDS_FRAGMENT, ShaderVariants.defines().depth(convention).build());
        description.colorFormat = VK_FORMAT_UNDEFINED;
        description.depthFormat = VK_FORMAT_D32_SFLOAT;
        description.stencilFormat = VK_FORMAT_UNDEFINED;
        description.depthTest = true;
        description.depthWrite = true;
        description.colorWrite = false;
        description.depthCompare = convention.reverseZ() ? VK_COMPARE_OP_LESS : VK_COMPARE_OP_GREATER;
        description.bindings = List.of(PipelineBindings.uniform(0), PipelineBindings.storage(1));
        return GraphicsPipeline.create(commands, description);
    }

    public void record(VulkanViewTargets targets, VisibleBoundsBuffer visible) {
        visible.prepare();
        int count = visible.count();
        if (count != 0) scene.publish(uploads, uniform, targets);
        var command = commands.command();
        ImageTransitions.transition(targets.attachments.depthBound, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT,
                VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        try (var stack = stackPush()) {
            var depth = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(targets.attachments.depthBound.view)
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depth.clearValue().depthStencil().depth(convention.inverseClearDepth()).stencil(0);
            var rendering = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(VkRect2D.calloc(stack).extent(e -> e.width(targets.state.width).height(targets.state.height)))
                    .layerCount(1).pDepthAttachment(depth);
            vkCmdBeginRenderingKHR(command, rendering);
        }
        if (count != 0) {
            pipeline.bind(command);
            RenderCommands.viewport(command, targets.state.width, targets.state.height);
            try (var descriptors = pipeline.descriptors()) {
                descriptors.uniform(0, uniform).storage(1, (GpuBuffer) visible.buffer()).push(command);
            }
            vkCmdBindIndexBuffer(command, indices.buffer, 0, VK_INDEX_TYPE_UINT16);
            vkCmdDrawIndexed(command, 36, count, 0, 0, 0);
        }
        vkCmdEndRenderingKHR(command);
        ImageTransitions.transition(targets.attachments.depthBound, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
    }

    public void close() {
        pipeline.free();
        uniform.free();
        indices.free();
    }
}

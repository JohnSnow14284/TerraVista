package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import java.util.List;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfoKHR;
import org.lwjgl.vulkan.VkRenderingInfoKHR;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;

/** 初始化远景深度和模板，并管理深度附件的采样转换。 */
final class DepthSetupPass {
    private final FrameCommands commands;
    private final CompositePipelines pipelines;
    private final DepthConvention convention;
    private final long sampler;

    DepthSetupPass(FrameCommands commands, CompositePipelines pipelines, DepthConvention convention, long sampler) {
        this.commands = commands;
        this.pipelines = pipelines;
        this.convention = convention;
        this.sampler = sampler;
    }

    void record(FrameAttachments frame) {
        pipelines.setup(frame.view());
        var command = commands.command();
        var view = frame.view();
        var targets = view.attachments;
        ImageTransitions.batch(List.of(
                new ImageTransitions.Change(targets.colour, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT),
                new ImageTransitions.Change(targets.depthStencil, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                        VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)),
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                        | VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT);
        MinecraftFrameAccess.transition(command, frame.depth(), true,
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        begin(command, view);
        pipelines.setup.bind(command);
        RenderCommands.viewport(command, view.state.width, view.state.height);
        try (var bindings = pipelines.setup.descriptors()) {
            bindings.sampler(0, MinecraftFrameAccess.view(frame.depth()), sampler,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL).push(command);
        }
        try (var stack = stackPush()) {
            var scale = stack.malloc(8);
            scale.putFloat(0, ((float) view.state.width) / frame.width());
            scale.putFloat(4, ((float) view.state.height) / frame.height());
            pipelines.setup.pushConstants(command, scale);
        }
        vkCmdDraw(command, 4, 1, 0, 0);
        vkCmdEndRenderingKHR(command);
        MinecraftFrameAccess.transition(command, frame.depth(), true,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL);
    }

    private void begin(org.lwjgl.vulkan.VkCommandBuffer command, VulkanViewTargets view) {
        try (var stack = stackPush()) {
            var colour = VkRenderingAttachmentInfoKHR.calloc(1, stack).sType$Default()
                    .imageView(view.attachments.colour.view).imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colour.clearValue().color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
            var depth = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(view.attachments.depthStencil.view).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depth.clearValue().depthStencil().depth(convention.clearDepth()).stencil(1);
            var stencil = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(view.attachments.depthStencil.view).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            stencil.clearValue().depthStencil().depth(convention.clearDepth()).stencil(1);
            var info = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(VkRect2D.calloc(stack).extent(e -> e.width(view.state.width).height(view.state.height)))
                    .layerCount(1).pColorAttachments(colour).pDepthAttachment(depth).pStencilAttachment(stencil);
            vkCmdBeginRenderingKHR(command, info);
        }
    }

    static void sampled(VulkanViewTargets view) {
        ImageTransitions.transition(view.attachments.depthStencil, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
    }

    static void attachment(VulkanViewTargets view) {
        ImageTransitions.transition(view.attachments.depthStencil, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT,
                VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
    }
}

package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import dev.terravista.config.ClientOptions;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfoKHR;
import org.lwjgl.vulkan.VkRenderingInfoKHR;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;

/** 管理合成资源、雾参数及宿主颜色与深度合成。 */
public final class CompositePass {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final boolean environmentalFog;
    private final CompositePipelines pipelines;
    private final DepthSetupPass setup;
    private final GpuBuffer parameters;
    private final long depthSampler, colourSampler;
    private final Matrix4f scratch = new Matrix4f();

    public CompositePass(FrameCommands commands, VulkanUploads uploads, DepthConvention convention, boolean environmentalFog) {
        this.commands = commands;
        this.uploads = uploads;
        this.environmentalFog = environmentalFog;
        pipelines = new CompositePipelines(commands, convention, environmentalFog);
        parameters = new GpuBuffer(commands, 160);
        try {
            parameters.zero();
            depthSampler = DeviceSamplers.get(commands.device(), false, false);
            colourSampler = DeviceSamplers.get(commands.device(), false, false);
        } catch (RuntimeException | Error failure) {
            parameters.free();
            throw failure;
        }
        setup = new DepthSetupPass(commands, pipelines, convention, depthSampler);
    }

    public void initialize(FrameAttachments frame) {
        setup.record(frame);
    }

    public static boolean hiddenByFog(VulkanViewTargets view) {
        return view.state.fog != null && view.state.fog.environmentalEnd() < hostDistance();
    }

    private static float hostDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    public void record(FrameAttachments frame) {
        var view = frame.view();
        if (hiddenByFog(view)) return;
        int colourFormat = MinecraftFrameAccess.format(frame.colour());
        int depthFormat = MinecraftFrameAccess.format(frame.depth());
        pipelines.composite(colourFormat, depthFormat);
        var command = commands.command();
        publish(view);
        ImageTransitions.transition(view.attachments.shadedColour, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
        try (var stack = stackPush()) {
            var colour = VkRenderingAttachmentInfoKHR.calloc(1, stack).sType$Default()
                    .imageView(MinecraftFrameAccess.view(frame.colour())).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            var depth = VkRenderingAttachmentInfoKHR.calloc(stack).sType$Default()
                    .imageView(MinecraftFrameAccess.view(frame.depth())).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            var info = VkRenderingInfoKHR.calloc(stack).sType$Default()
                    .renderArea(VkRect2D.calloc(stack).extent(e -> e.width(frame.width()).height(frame.height())))
                    .layerCount(1).pColorAttachments(colour).pDepthAttachment(depth);
            vkCmdBeginRenderingKHR(command, info);
        }
        pipelines.composite.bind(command);
        RenderCommands.viewport(command, frame.width(), frame.height());
        try (var bindings = pipelines.composite.descriptors()) {
            bindings.sampler(0, view.attachments.depthSampleView, depthSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .uniform(1, parameters)
                    .sampler(3, view.attachments.shadedColour.view, colourSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .push(command);
        }
        vkCmdDraw(command, 4, 1, 0, 0);
        vkCmdEndRenderingKHR(command);
    }

    private void publish(VulkanViewTargets view) {
        long pointer = uploads.upload(parameters, 0, parameters.size());
        scratch.set(view.state.transform).invert().getToAddress(pointer);
        scratch.set(view.state.sourceProjection).mul(view.state.modelView).getToAddress(pointer + 64);
        float e0 = 0, e1 = 0, e2 = 0, f0 = 0, f1 = 0, f2 = 0, f3 = 0;
        if (environmentalFog && view.state.fog != null) {
            float start = view.state.fog.environmentalStart();
            float end = view.state.fog.environmentalEnd();
            if (Math.abs(end - start) > 1) {
                float inverseDelta = 1f / (end - start);
                float distance = Math.max(hostDistance(), 20 * 16) * (float) Math.sqrt(3);
                float startDelta = -start * inverseDelta;
                e0 = inverseDelta;
                e1 = startDelta;
                e2 = ClientOptions.CONFIG.fog() == ClientOptions.FogMode.CUSTOM
                        ? 1 : Math.clamp(distance * inverseDelta + startDelta, 0, 1);
                f0 = view.state.fog.red();
                f1 = view.state.fog.green();
                f2 = view.state.fog.blue();
                f3 = view.state.fog.alpha();
            }
        }
        memPutFloat(pointer + 128, e0);
        memPutFloat(pointer + 132, e1);
        memPutFloat(pointer + 136, e2);
        memPutFloat(pointer + 140, 0f);
        memPutFloat(pointer + 144, f0);
        memPutFloat(pointer + 148, f1);
        memPutFloat(pointer + 152, f2);
        memPutFloat(pointer + 156, f3);
        uploads.commit();
    }

    public void close() {
        pipelines.close();
        parameters.free();
    }
}

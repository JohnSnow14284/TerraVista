package dev.terravista.render.gpu;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.terravista.config.ClientOptions.SSAOMode;
import dev.terravista.render.DepthConvention;
import java.util.List;
import org.joml.Matrix4f;
import static org.lwjgl.vulkan.VK10.*;

/** 上传遮蔽参数并生成合成所需的颜色与覆盖率。 */
public final class AmbientOcclusionPass {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final AmbientOcclusionSettings settings;
    private final DevicePipeline pipeline;
    private final GpuBuffer parameters;
    private final long colourSampler, depthSampler;
    private final Matrix4f inverse = new Matrix4f();

    public AmbientOcclusionPass(FrameCommands commands, VulkanUploads uploads,
                                DepthConvention convention, SSAOMode mode) {
        this.commands = commands;
        this.uploads = uploads;
        settings = new AmbientOcclusionSettings(mode);
        var bindings = settings.advanced
                ? List.of(PipelineBindings.image(0), PipelineBindings.sampler(1), PipelineBindings.sampler(2),
                        PipelineBindings.sampler(3), PipelineBindings.uniform(4))
                : List.of(PipelineBindings.image(0), PipelineBindings.sampler(1), PipelineBindings.sampler(2),
                        PipelineBindings.uniform(4));
        pipeline = ComputePipeline.create(commands, ShaderProgram.OCCLUSION_APPLY.path, settings.shader(convention), 0, bindings);
        GpuBuffer created = null;
        try {
            parameters = created = new GpuBuffer(commands, settings.advanced ? 256 : 128);
            parameters.zero();
            colourSampler = DeviceSamplers.get(commands.device(), false, false);
            depthSampler = DeviceSamplers.get(commands.device(), false, !settings.advanced);
        } catch (RuntimeException | Error failure) {
            if (created != null) created.free();
            pipeline.free();
            throw failure;
        }
    }

    public void record(VulkanViewTargets view, GpuTextureView hostDepth) {
        var command = commands.command();
        long pointer = uploads.upload(parameters, 0, parameters.size());
        var state = view.state;
        if (settings.advanced) {
            state.projection.getToAddress(pointer);
            state.projection.invert(inverse).getToAddress(pointer + 64);
            state.modelView.getToAddress(pointer + 128);
            state.sourceProjection.invert(inverse).getToAddress(pointer + 192);
        } else {
            state.transform.getToAddress(pointer);
            state.transform.invert(inverse).getToAddress(pointer + 64);
        }
        uploads.commit();

        var targets = view.attachments;
        ImageTransitions.batch(List.of(
                new ImageTransitions.Change(targets.colour, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT),
                new ImageTransitions.Change(targets.depthStencil, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT),
                new ImageTransitions.Change(targets.shadedColour, VK_IMAGE_LAYOUT_GENERAL,
                        VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_SHADER_WRITE_BIT)),
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        if (settings.advanced) {
            MinecraftFrameAccess.transition(command, hostDepth, true,
                    VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
        pipeline.bind(command);
        try (var bindings = pipeline.descriptors()) {
            bindings.image(0, targets.shadedColour.view)
                    .sampler(1, targets.colour.view, colourSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .sampler(2, targets.depthSampleView, depthSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            if (settings.advanced) {
                bindings.sampler(3, MinecraftFrameAccess.view(hostDepth), depthSampler, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
            bindings.uniform(4, parameters).push(command);
        }
        vkCmdDispatch(command, (state.width + 7) / 8, (state.height + 7) / 8, 1);
        if (settings.advanced) {
            MinecraftFrameAccess.transition(command, hostDepth, true,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL);
        }
        ImageTransitions.batch(List.of(
                new ImageTransitions.Change(targets.shadedColour, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT),
                new ImageTransitions.Change(targets.depthStencil, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                        VK_ACCESS_SHADER_READ_BIT,
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)),
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT);
    }

    public void debug(List<String> lines) {
        lines.add("TerraVista SSAO: " + (settings.advanced ? "advanced (" + settings.samples + " spp)" : "basic"));
    }

    public void close() {
        pipeline.free();
        parameters.free();
    }
}

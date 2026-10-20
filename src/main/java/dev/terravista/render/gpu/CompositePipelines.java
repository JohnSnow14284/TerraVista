package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** 按附件格式缓存深度初始化与最终合成管线。 */
final class CompositePipelines {
    private final FrameCommands commands;
    private final DepthConvention convention;
    private final boolean environmentalFog;
    private int setupDepth = -1;
    private int compositeColour = -1, compositeDepth = -1;
    DevicePipeline setup, composite;

    CompositePipelines(FrameCommands commands, DepthConvention convention, boolean environmentalFog) {
        this.commands = commands;
        this.convention = convention;
        this.environmentalFog = environmentalFog;
    }

    void setup(VulkanViewTargets targets) {
        if (setup != null && setupDepth == targets.attachments.depthStencil.format) return;
        var description = new GraphicsPipeline.Description();
        description.name = "depth-setup";
        description.vertexSource = ShaderVariants.load(ShaderProgram.COMPOSITE_VERTEX, ShaderVariants.defines().depth(convention).build());
        description.fragmentSource = ShaderVariants.load(ShaderProgram.COMPOSITE_DEPTH, ShaderVariants.defines().depth(convention).build());
        description.pushBytes = 8;
        description.colorFormat = targets.attachments.colour.format;
        description.depthFormat = targets.attachments.depthStencil.format;
        description.stencilFormat = targets.attachments.depthStencil.format;
        description.depthTest = true;
        description.depthWrite = true;
        description.depthCompare = VK_COMPARE_OP_ALWAYS;
        description.colorWrite = false;
        description.blend = false;
        description.stencilWriteAlways1 = true;
        description.stencilWriteRef = 0;
        description.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
        description.bindings = List.of(PipelineBindings.sampler(0));
        var replacement = GraphicsPipeline.create(commands, description);
        if (setup != null) setup.free();
        setup = replacement;
        setupDepth = targets.attachments.depthStencil.format;
    }

    void composite(int colour, int depth) {
        if (composite != null && compositeColour == colour && compositeDepth == depth) return;
        var description = new GraphicsPipeline.Description();
        description.name = "composite";
        description.vertexSource = ShaderVariants.load(ShaderProgram.COMPOSITE_VERTEX, ShaderVariants.defines().depth(convention).build());
        description.fragmentSource = ShaderVariants.load(ShaderProgram.COMPOSITE_RESOLVE, ShaderVariants.defines().depth(convention)
                .define("EMIT_COLOUR").when("HAS_FOG", environmentalFog).build());
        description.colorFormat = colour;
        description.depthFormat = depth;
        description.stencilFormat = VK_FORMAT_UNDEFINED;
        description.depthTest = true;
        description.depthWrite = true;
        description.depthCompare = convention.reverseZ() ? VK_COMPARE_OP_GREATER_OR_EQUAL : VK_COMPARE_OP_LESS_OR_EQUAL;
        description.blend = true;
        description.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
        description.bindings = List.of(PipelineBindings.sampler(0), PipelineBindings.uniform(1), PipelineBindings.sampler(3));
        var replacement = GraphicsPipeline.create(commands, description);
        if (composite != null) composite.free();
        composite = replacement;
        compositeColour = colour;
        compositeDepth = depth;
    }

    void close() {
        if (setup != null) setup.free();
        if (composite != null) composite.free();
    }
}

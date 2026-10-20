package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import java.util.List;
import net.minecraft.client.Minecraft;
import static org.lwjgl.vulkan.VK10.*;

/** 按附件格式创建并替换地形材质管线。 */
final class TerrainMaterials {
    private final FrameCommands commands;
    private final DepthConvention convention;
    private int colorFormat = -1, depthFormat = -1;
    DevicePipeline opaque, translucent;

    TerrainMaterials(FrameCommands commands, DepthConvention convention) {
        this.commands = commands;
        this.convention = convention;
    }

    void ensure(VulkanViewTargets targets) {
        int color = targets.attachments.colour.format;
        int depth = targets.attachments.depthStencil.format;
        if (opaque != null && color == colorFormat && depth == depthFormat) return;
        var lighting = Minecraft.getInstance().level.cardinalLighting();
        String vertex = ShaderVariants.load(ShaderProgram.TERRAIN_VERTEX, ShaderVariants.defines().depth(convention)
                .define("NO_SHADE_FACE_TINT", lighting.up()).define("UP_FACE_TINT", lighting.up())
                .define("DOWN_FACE_TINT", lighting.down()).define("Z_AXIS_FACE_TINT", lighting.north())
                .define("X_AXIS_FACE_TINT", lighting.east()).build());
        var bindings = List.of(PipelineBindings.uniform(0), PipelineBindings.storage(1), PipelineBindings.storage(3),
                PipelineBindings.storage(4), PipelineBindings.storage(5), PipelineBindings.sampler(8),
                PipelineBindings.sampler(9), PipelineBindings.sampler(10));
        var solid = description(false, vertex, color, depth, bindings);
        var alpha = description(true, vertex, color, depth, bindings);
        var nextOpaque = GraphicsPipeline.create(commands, solid);
        DevicePipeline nextTranslucent;
        try {
            nextTranslucent = GraphicsPipeline.create(commands, alpha);
        } catch (RuntimeException | Error failure) {
            nextOpaque.free();
            throw failure;
        }
        if (opaque != null) {
            opaque.free();
            translucent.free();
        }
        opaque = nextOpaque;
        translucent = nextTranslucent;
        colorFormat = color;
        depthFormat = depth;
    }

    private GraphicsPipeline.Description description(boolean alpha, String vertex, int color, int depth,
                                                      List<PipelineBindings.Entry> bindings) {
        var description = new GraphicsPipeline.Description();
        description.name = alpha ? "terrain-translucent" : "terrain-opaque";
        description.vertexSource = vertex;
        description.fragmentSource = ShaderVariants.load(ShaderProgram.TERRAIN_FRAGMENT, ShaderVariants.defines().depth(convention)
                .when("TERRAVISTA_VULKAN_SAMPLE_MASK_DISCARD", commands.device().sampleMaskDiscard)
                .when("TRANSLUCENT", alpha).build());
        description.colorFormat = color;
        description.depthFormat = depth;
        description.stencilFormat = depth;
        description.depthTest = true;
        description.depthWrite = true;
        description.depthCompare = convention.reverseZ() ? VK_COMPARE_OP_GREATER_OR_EQUAL : VK_COMPARE_OP_LESS_OR_EQUAL;
        description.blend = alpha;
        description.stencilTestEqual1 = true;
        description.bindings = bindings;
        return description;
    }

    void close() {
        if (opaque != null) {
            opaque.free();
            translucent.free();
        }
    }
}

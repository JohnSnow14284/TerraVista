package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import java.util.List;

/** 管理地形命令生成、透明排序与前缀和计算管线。 */
final class TerrainPipelines {
    static final int TRANSLUCENT_OFFSET = VulkanViewTargets.OPAQUE_CAPACITY;
    static final int TEMPORAL_OFFSET = TRANSLUCENT_OFFSET + VulkanViewTargets.TRANSLUCENT_CAPACITY;
    static final int TRANSLUCENT_DISPATCH_OFFSET = 64;
    private final FrameCommands commands;
    private final DepthConvention convention;
    final DevicePipeline prepare, generate, prefix, translucent;

    TerrainPipelines(FrameCommands commands, DepthConvention convention) {
        this.commands = commands;
        this.convention = convention;
        DevicePipeline[] created = new DevicePipeline[4];
        try {
            prepare = created[0] = ComputePipeline.create(commands, ShaderProgram.DRAW_PREPARE.path,
                    ShaderVariants.load(ShaderProgram.DRAW_PREPARE, ShaderVariants.defines().build()),
                    0, List.of(PipelineBindings.uniform(0), PipelineBindings.storage(1), PipelineBindings.storage(2)));
            generate = created[1] = createCommands(true);
            var scan = commands.device().capabilities.arithmetic
                    ? ShaderProgram.SCAN_SUBGROUP : ShaderProgram.SCAN_SERIAL;
            prefix = created[2] = ComputePipeline.create(commands, scan.path,
                    ShaderVariants.load(scan, ShaderVariants.defines().define("IO_BUFFER", 0)
                            .define("TRANSLUCENT_DISPATCH_WORD", TRANSLUCENT_DISPATCH_OFFSET / 4)
                            .define("CLEAR_DISPATCH_WORD", IndirectCommandClear.DISPATCH_OFFSET / 4)
                            .define("OPAQUE_CAPACITY", VulkanViewTargets.OPAQUE_CAPACITY)
                            .define("TRANSLUCENT_CAPACITY", VulkanViewTargets.TRANSLUCENT_CAPACITY)
                            .define("TEMPORAL_CAPACITY", VulkanViewTargets.TEMPORAL_CAPACITY).build()),
                    0, List.of(PipelineBindings.storage(0), PipelineBindings.storage(1)));
            translucent = created[3] = ComputePipeline.create(commands, ShaderProgram.DRAW_TRANSLUCENT.path,
                    ShaderVariants.load(ShaderProgram.DRAW_TRANSLUCENT, ShaderVariants.defines()
                            .define("TRANSLUCENT_WRITE_BASE", 1024)
                            .define("TRANSLUCENT_CAPACITY", VulkanViewTargets.TRANSLUCENT_CAPACITY)
                            .define("TRANSLUCENT_DISTANCE_BUFFER_BINDING", 5)
                            .define("TRANSLUCENT_OFFSET", TRANSLUCENT_OFFSET).build()),
                    0, List.of(PipelineBindings.uniform(0), PipelineBindings.storage(1), PipelineBindings.storage(2),
                            PipelineBindings.storage(3), PipelineBindings.storage(4), PipelineBindings.storage(5)));
        } catch (RuntimeException | Error failure) {
            for (var pipeline : created) if (pipeline != null) pipeline.free();
            throw failure;
        }
    }

    private DevicePipeline createCommands(boolean compact) {
        return ComputePipeline.create(commands, compact ? "draw/commands-compact.comp" : ShaderProgram.DRAW_COMMANDS.path,
                ShaderVariants.load(ShaderProgram.DRAW_COMMANDS, ShaderVariants.defines().depth(convention)
                        .when("COMPACT_COMMANDS", compact)
                        .define("TRANSLUCENT_WRITE_BASE", 1024)
                        .define("TRANSLUCENT_CAPACITY", VulkanViewTargets.TRANSLUCENT_CAPACITY)
                        .define("TEMPORAL_OFFSET", TEMPORAL_OFFSET)
                        .define("OPAQUE_CAPACITY", VulkanViewTargets.OPAQUE_CAPACITY)
                        .define("TEMPORAL_CAPACITY", VulkanViewTargets.TEMPORAL_CAPACITY)
                        .define("TRANSLUCENT_DISTANCE_BUFFER_BINDING", 7).build()),
                4, List.of(PipelineBindings.uniform(0), PipelineBindings.storage(1), PipelineBindings.storage(2),
                        PipelineBindings.storage(3), PipelineBindings.storage(4), PipelineBindings.storage(5),
                        PipelineBindings.storage(6), PipelineBindings.storage(7), PipelineBindings.sampler(8)));
    }

    void close() {
        prepare.free();
        generate.free();
        prefix.free();
        translucent.free();
    }
}

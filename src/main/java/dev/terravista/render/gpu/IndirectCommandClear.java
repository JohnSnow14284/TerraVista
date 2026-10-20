package dev.terravista.render.gpu;

import java.util.List;
import static org.lwjgl.vulkan.VK10.*;

/** 在计数器复位前清理上次写入的绘制命令。 */
public final class IndirectCommandClear {
    public static final int DISPATCH_OFFSET = 80;
    private final FrameCommands commands;
    private final DevicePipeline pipeline;

    public IndirectCommandClear(FrameCommands commands) {
        this.commands = commands;
        var variants = ShaderVariants.defines()
                .define("OPAQUE_CAPACITY", VulkanViewTargets.OPAQUE_CAPACITY)
                .define("TRANSLUCENT_CAPACITY", VulkanViewTargets.TRANSLUCENT_CAPACITY)
                .define("TEMPORAL_CAPACITY", VulkanViewTargets.TEMPORAL_CAPACITY);
        pipeline = ComputePipeline.create(commands, ShaderProgram.DRAW_RESET.path,
                ShaderVariants.load(ShaderProgram.DRAW_RESET, variants.build()), 0,
                List.of(PipelineBindings.storage(0), PipelineBindings.storage(1)));
    }

    public void record(GpuBuffer drawCommands, GpuBuffer counters) {
        var command = commands.command();
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_INDIRECT_COMMAND_READ_BIT | VK_ACCESS_SHADER_READ_BIT);
        pipeline.bind(command);
        try (var writes = pipeline.descriptors()) {
            writes.storage(0, counters).storage(1, drawCommands).push(command);
        }
        vkCmdDispatchIndirect(command, counters.buffer, DISPATCH_OFFSET);
        // 同步旧计数器读取与命令写入。
        commands.computeToCompute();
    }

    public void close() { pipeline.free(); }
}

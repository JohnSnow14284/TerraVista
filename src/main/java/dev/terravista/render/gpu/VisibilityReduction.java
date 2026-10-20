package dev.terravista.render.gpu;

import java.util.List;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 通过四种计算变体选取最早的 256 个候选节点。 */
public final class VisibilityReduction {
    private final FrameCommands commands;
    private final DevicePipeline[] passes = new DevicePipeline[4];
    private final VisibilityCandidates candidates;

    public VisibilityReduction(FrameCommands commands) {
        this.commands = commands;
        candidates = new VisibilityCandidates(commands);
        try {
            for (int mode = 0; mode < passes.length; mode++) {
                boolean first = (mode & 1) != 0;
                passes[mode] = ComputePipeline.create(commands, "visibility/minimum-" + mode,
                        ShaderVariants.load(ShaderProgram.VISIBILITY_MINIMUM, ShaderVariants.defines()
                                .when("FIRST_PASS", first).when("FINAL_PASS", (mode & 2) != 0)
                                .define("NODE_DATA_BINDING", 3).build()), 4,
                        first ? List.of(PipelineBindings.storage(1), PipelineBindings.storage(2), PipelineBindings.storage(3))
                                : List.of(PipelineBindings.storage(0), PipelineBindings.storage(2)));
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    public void select(GpuBuffer visibility, GpuBuffer nodes, GpuBuffer output, int nodeCount) {
        if (nodeCount < 0 || nodeCount > visibility.size() / 4 || nodeCount > nodes.size() / 16)
            throw new IllegalArgumentException("TerraVista Render: Cleaner scan outside node buffers");
        int groups = Math.max(1, (nodeCount + 511) / 512);
        if (groups > 1) candidates.ensure(groups);
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        int count = nodeCount;
        boolean first = true;
        GpuBuffer input = null;
        while (true) {
            boolean last = groups == 1;
            GpuBuffer target = last ? output : candidates.target(input);
            var pipeline = passes[(first ? 1 : 0) | (last ? 2 : 0)];
            pipeline.bind(commands.command());
            try (var writes = pipeline.descriptors()) {
                if (first) writes.storage(1, visibility).storage(3, nodes);
                else writes.storage(0, input);
                writes.storage(2, target).push(commands.command());
            }
            try (var stack = stackPush()) {
                pipeline.pushConstants(commands.command(), stack.malloc(4).putInt(0, count));
            }
            vkCmdDispatch(commands.command(), groups, 1, 1);
            if (last) return;
            commands.computeToCompute();
            input = target;
            count = groups * 256;
            groups = (count + 511) / 512;
            first = false;
        }
    }

    public void close() {
        for (var pipeline : passes) if (pipeline != null) pipeline.free();
        candidates.close();
    }
}

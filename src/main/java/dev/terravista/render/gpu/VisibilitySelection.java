package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import java.util.List;
import org.lwjgl.vulkan.VkCommandBuffer;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static dev.terravista.render.tree.VisibilityEviction.OUTPUT_COUNT;

/** 管理可选归约及持续生效的原子回退。 */
final class VisibilitySelection {
    private static final int WORKERS = 64, ITEMS_PER_WORKER = 8;
    private final FrameCommands commands;
    private final DevicePipeline atomic;
    private VisibilityReduction reduction;
    private boolean unavailable;

    VisibilitySelection(FrameCommands commands) {
        this.commands = commands;
        atomic = ComputePipeline.create(commands, ShaderProgram.VISIBILITY_RANK.path,
                ShaderVariants.load(ShaderProgram.VISIBILITY_RANK, ShaderVariants.defines()
                        .define("WORK_SIZE", WORKERS).define("ELEMS_PER_THREAD", ITEMS_PER_WORKER)
                        .define("OUTPUT_SIZE", OUTPUT_COUNT).define("VISIBILITY_BUFFER_BINDING", 1)
                        .define("OUTPUT_BUFFER_BINDING", 2).define("NODE_DATA_BINDING", 3).build()),
                4, List.of(PipelineBindings.storage(1), PipelineBindings.storage(2), PipelineBindings.storage(3)));
    }

    void select(VkCommandBuffer command, GpuBuffer visibility, GpuBuffer nodes, GpuBuffer output, int count) {
        boolean reduced = false;
        if (!unavailable) {
            try {
                if (reduction == null) reduction = new VisibilityReduction(commands);
                reduction.select(visibility, nodes, output, count);
                reduced = true;
            } catch (VulkanCalls.Failure failure) {
                if (!failure.outOfMemory()) throw failure;
                if (reduction != null) reduction.close();
                reduction = null;
                unavailable = true;
                ModLog.warn("Render: Vulkan cleaner reduction ran out of memory; using atomic cleanup until the renderer is reloaded");
            }
        }
        if (!reduced) {
            commands.fillBuffer(output.buffer, 0, 4L * OUTPUT_COUNT, -1);
            atomic.bind(command);
            try (var writes = atomic.descriptors()) {
                writes.storage(1, visibility).storage(2, output).storage(3, nodes).push(command);
            }
            try (var stack = stackPush()) {
                atomic.pushConstants(command, stack.malloc(4).putInt(0, count));
            }
            vkCmdDispatch(command, (count + WORKERS * ITEMS_PER_WORKER - 1) / (WORKERS * ITEMS_PER_WORKER), 1, 1);
        }
    }

    void close() {
        if (reduction != null) reduction.close();
        atomic.free();
    }
}

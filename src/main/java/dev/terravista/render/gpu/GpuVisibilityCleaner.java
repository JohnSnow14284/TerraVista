package dev.terravista.render.gpu;

import dev.terravista.render.DeviceBuffer;
import dev.terravista.render.tree.TreePipeline;
import dev.terravista.render.tree.VisibilityEviction;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

/** 根据可见性与显存压力异步回收树节点。 */
public final class GpuVisibilityCleaner implements VisibilityEviction {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final VulkanReadbacks readbacks;
    private final TreePipeline tree;
    private final VisibilitySelection selection;
    private final DevicePipeline transformer, slots;
    public final GpuBuffer visibility;
    private final GpuBuffer output;
    int epoch;

    public GpuVisibilityCleaner(FrameCommands commands, VulkanUploads uploads, VulkanReadbacks readbacks, TreePipeline tree) {
        this.commands = commands;
        this.uploads = uploads;
        this.readbacks = readbacks;
        this.tree = tree;
        GpuBuffer[] buffers = new GpuBuffer[2];
        DevicePipeline[] pipelines = new DevicePipeline[2];
        VisibilitySelection createdSelection = null;
        try {
            visibility = buffers[0] = new GpuBuffer(commands, tree.maxNodes * 4L);
            visibility.fill(-1);
            output = buffers[1] = new GpuBuffer(commands, OUTPUT_COUNT * 4 + OUTPUT_COUNT * 8);
            selection = createdSelection = new VisibilitySelection(commands);
            transformer = pipelines[0] = ComputePipeline.create(commands, ShaderProgram.VISIBILITY_EVICT.path,
                    ShaderVariants.load(ShaderProgram.VISIBILITY_EVICT, ShaderVariants.defines()
                            .define("OUTPUT_SIZE", OUTPUT_COUNT).define("MIN_ID_BUFFER_BINDING", 0)
                            .define("NODE_BUFFER_BINDING", 1).define("OUTPUT_BUFFER_BINDING", 2)
                            .define("VISIBILITY_BUFFER_BINDING", 3).build()),
                    4, List.of(PipelineBindings.storage(0), PipelineBindings.storage(1), PipelineBindings.storage(2), PipelineBindings.storage(3)));
            slots = pipelines[1] = ComputePipeline.create(commands, ShaderProgram.VISIBILITY_MARK.path,
                    ShaderVariants.load(ShaderProgram.VISIBILITY_MARK, ShaderVariants.defines()
                            .define("VISIBILITY_BUFFER_BINDING", 0).define("LIST_BUFFER_BINDING", 1).build()),
                    8, List.of(PipelineBindings.storage(0), PipelineBindings.storage(1)));
        } catch (RuntimeException | Error failure) {
            if (createdSelection != null) createdSelection.close();
            for (var pipeline : pipelines) if (pipeline != null) pipeline.free();
            // 延迟释放当前帧可能已引用的可见性资源。
            for (var buffer : buffers) if (buffer != null) buffer.free();
            throw failure;
        }
    }

    @Override public void processFrame(DeviceBuffer input) {
        var nodes = (GpuBuffer) input;
        epoch++;
        if (underPressure()) {
            int count = tree.lastNode() + 1;
            if (count == 0) return;
            var command = commands.command();
            selection.select(command, visibility, nodes, output, count);
            commands.computeToCompute();
            transformer.bind(command);
            try (var writes = transformer.descriptors()) {
                writes.storage(0, output.buffer, 0, 4L * OUTPUT_COUNT).storage(1, nodes)
                        .storage(2, output.buffer, 4L * OUTPUT_COUNT, 8L * OUTPUT_COUNT).storage(3, visibility).push(command);
            }
            try (var stack = stackPush()) {
                transformer.pushConstants(command, stack.malloc(4).putInt(0, epoch));
            }
            vkCmdDispatch(command, 1, 1, 1);
            readbacks.download(output, 4L * OUTPUT_COUNT, 8L * OUTPUT_COUNT, result -> tree.removal(result.duplicate()));
        }
    }

    private boolean underPressure() {
        long remaining = tree.geometryCapacity() - tree.usedBytes();
        return remaining < 256_000_000;
    }

    @Override public void updateSlots(IntOpenHashSet collection) {
        if (!collection.isEmpty()) {
            int count = collection.size();
            long offset = uploads.rawUploadAddress(count * 4);
            long pointer = uploads.baseAddress() + offset;
            var iterator = collection.iterator();
            while (iterator.hasNext()) {
                memPutInt(pointer, iterator.nextInt());
                pointer += 4;
            }
            uploads.commit();
            var command = commands.command();
            slots.bind(command);
            try (var writes = slots.descriptors()) {
                writes.storage(0, visibility).storage(1, uploads.stagingBuffer(), offset, uploads.alignAllocation(count * 4)).push(command);
            }
            try (var stack = stackPush()) {
                slots.pushConstants(command, stack.malloc(8).putInt(0, count).putInt(4, epoch));
            }
            vkCmdDispatch(command, (count + 127) / 128, 1, 1);
            commands.computeToCompute();
        }
    }

    @Override public void close() {
        selection.close();
        transformer.free();
        slots.free();
        visibility.free();
        output.free();
    }
}

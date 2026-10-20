package dev.terravista.render.gpu;

import dev.terravista.memory.NativeMemory;
import dev.terravista.render.DeviceBuffer;
import dev.terravista.render.tree.MeshBufferStorage;
import dev.terravista.render.tree.NodeUploadTarget;
import java.util.List;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

/** 将打包的散写记录应用到树节点缓冲区。 */
public final class GpuNodeUpdates implements NodeUploadTarget {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final DevicePipeline scatter;
    private final GeometryTransfers transfers;

    public GpuNodeUpdates(FrameCommands commands, VulkanUploads uploads) {
        this.commands = commands;
        this.uploads = uploads;
        scatter = ComputePipeline.create(commands, ShaderProgram.TRANSFER_SCATTER.path,
                ShaderVariants.load(ShaderProgram.TRANSFER_SCATTER, ShaderVariants.defines()
                        .define("INPUT_BUFFER_BINDING", 0).define("OUTPUT_BUFFER1_BINDING", 1)
                        .define("OUTPUT_BUFFER2_BINDING", 2).build()),
                4, List.of(PipelineBindings.storage(0), PipelineBindings.storage(1), PipelineBindings.storage(2)));
        try {
            transfers = new GeometryTransfers(commands, uploads);
        } catch (RuntimeException | Error failure) {
            scatter.free();
            throw failure;
        }
    }
    @Override public void copyRanges(long headers, int copies, long data, int bytes, MeshBufferStorage geometry) {
        transfers.copy(headers, copies, data, bytes, geometry);
    }
    @Override public void scatter(long chunks, int count, DeviceBuffer nodes, MeshBufferStorage geometry) {
        int groupsOfFour = (count + 3) / 4;
        int bytes = groupsOfFour * 80;
        long offset = uploads.rawUploadAddress(bytes);
        NativeMemory.copy(chunks, uploads.baseAddress() + offset, bytes);
        uploads.commit();
        var command = commands.command();
        scatter.bind(command);
        try (var writes = scatter.descriptors()) {
            writes.storage(0, uploads.stagingBuffer(), offset, uploads.alignAllocation(bytes))
                    .storage(1, (GpuBuffer) nodes).storage(2, (GpuBuffer) geometry.metadataBuffer()).push(command);
        }
        try (var stack = stackPush()) {
            scatter.pushConstants(command, stack.malloc(4).putInt(0, count));
        }
        vkCmdDispatch(command, (count + 127) / 128, 1, 1);
        commands.computeToCompute();
    }
    @Override public void close() {
        scatter.free();
        transfers.close();
    }
}

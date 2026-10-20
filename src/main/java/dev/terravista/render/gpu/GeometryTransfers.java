package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeMemory;
import dev.terravista.render.tree.MeshBufferStorage;
import java.util.List;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

/** 按范围选择原生复制或计算管线复制。 */
final class GeometryTransfers {
    private final FrameCommands commands;
    private final VulkanUploads uploads;
    private final DevicePipeline serial;
    private DevicePipeline parallel;

    GeometryTransfers(FrameCommands commands, VulkanUploads uploads) {
        this.commands = commands;
        this.uploads = uploads;
        serial = pipeline(false);
    }
    private DevicePipeline pipeline(boolean split) {
        return ComputePipeline.create(commands, split ? "transfer/copy-parallel.comp" : ShaderProgram.TRANSFER_COPY.path,
                ShaderVariants.load(ShaderProgram.TRANSFER_COPY, ShaderVariants.defines().when("PARALLEL_COPY", split)
                        .define("INPUT_HEADER_BUFFER_BINDING", 0).define("INPUT_DATA_BUFFER_BINDING", 1)
                        .define("OUTPUT_BUFFER_BINDING", 2).build()),
                0, List.of(PipelineBindings.storage(0), PipelineBindings.storage(1), PipelineBindings.storage(2)));
    }
    void copy(long headers, int copies, long data, int bytes, MeshBufferStorage geometry) {
        if (copies <= 0) return;
        if ((long) bytes < copies * 8192L) {
            var target = geometry.geometryBuffer();
            for (int i = 0; i < copies; i++) {
                long header = headers + i * 16L;
                long source = Integer.toUnsignedLong(memGetInt(header)) * 8;
                long destination = Integer.toUnsignedLong(memGetInt(header + 4)) * 8;
                long length = Integer.toUnsignedLong(memGetInt(header + 8)) * 8;
                if (source + length > bytes || destination + length > target.sizeBytes())
                    throw new IllegalArgumentException("TerraVista Render: Geometry copy outside source or destination");
                if (length != 0) NativeMemory.copy(data + source, uploads.upload(target, destination, length), length);
            }
            uploads.commit();
            return;
        }
        int headerBytes = uploads.alignAllocation(copies * 16);
        int dataBytes = uploads.alignAllocation(bytes);
        long offset = uploads.rawUploadAddress(dataBytes + headerBytes);
        NativeMemory.copy(headers, uploads.baseAddress() + offset, copies * 16L);
        NativeMemory.copy(data, uploads.baseAddress() + offset + headerBytes, bytes);
        uploads.commit();
        if (copies > 500) ModLog.warn("Render: Large amount of copies, lag will probably happen: " + copies);
        var command = commands.command();
        int groups = 1;
        if (copies > 0) {
            long average = (bytes + (long) copies - 1) / copies;
            groups = (int) Math.max(1, Math.min(32,
                    Math.min((256L + copies - 1) / copies, (average + 8191) / 8192)));
        }
        var selected = serial;
        if (groups > 1) {
            if (parallel == null) parallel = pipeline(true);
            selected = parallel;
        }
        selected.bind(command);
        try (var writes = selected.descriptors()) {
            writes.storage(0, uploads.stagingBuffer(), offset, headerBytes)
                    .storage(1, uploads.stagingBuffer(), offset + headerBytes, dataBytes)
                    .storage(2, (GpuBuffer) geometry.geometryBuffer()).push(command);
        }
        vkCmdDispatch(command, copies, groups, 1);
        commands.computeToCompute();
    }
    void close() {
        serial.free();
        if (parallel != null) parallel.free();
    }
}

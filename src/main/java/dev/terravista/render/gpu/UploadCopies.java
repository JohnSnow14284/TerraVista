package dev.terravista.render.gpu;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.vulkan.VkBufferCopy;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 按目标顺序与区域批量上限记录暂存拷贝。 */
final class UploadCopies {
    private record Copy(GpuBuffer target, long sourceOffset, long destinationOffset, long bytes) {}
    private final Deque<Copy> pending = new ArrayDeque<>();
    private final Reference2LongOpenHashMap<GpuBuffer> starts = new Reference2LongOpenHashMap<>();
    private final Reference2LongOpenHashMap<GpuBuffer> ends = new Reference2LongOpenHashMap<>();

    boolean isEmpty() { return pending.isEmpty(); }
    void add(GpuBuffer target, long sourceOffset, long destinationOffset, long bytes) {
        pending.add(new Copy(target, sourceOffset, destinationOffset, bytes));
    }
    void record(FrameCommands commands, long stagingBuffer) {
        var command = commands.command();
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
                        | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
                        | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT);
        try (var stack = stackPush()) {
            var regions = VkBufferCopy.calloc(Math.min(pending.size(), 256), stack);
            GpuBuffer target = null;
            int count = 0;
            for (var copy : pending) {
                long end = copy.destinationOffset + copy.bytes;
                boolean tracked = ends.containsKey(copy.target);
                boolean overlap = tracked && ends.getLong(copy.target) > copy.destinationOffset
                        && starts.getLong(copy.target) < end;
                if (count != 0 && (target != copy.target || overlap || count == regions.capacity())) {
                    regions.limit(count);
                    vkCmdCopyBuffer(command, stagingBuffer, target.buffer, regions);
                    regions.limit(regions.capacity());
                    count = 0;
                }
                if (overlap) {
                    commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
                    starts.clear();
                    ends.clear();
                    tracked = false;
                }
                regions.get(count++).srcOffset(copy.sourceOffset).dstOffset(copy.destinationOffset).size(copy.bytes);
                target = copy.target;
                starts.put(target, tracked ? Math.min(starts.getLong(target), copy.destinationOffset) : copy.destinationOffset);
                ends.put(target, tracked ? Math.max(ends.getLong(target), end) : end);
            }
            regions.limit(count);
            vkCmdCopyBuffer(command, stagingBuffer, target.buffer, regions);
        }
        starts.clear();
        ends.clear();
        pending.clear();
        commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_INPUT_BIT
                        | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_UNIFORM_READ_BIT
                        | VK_ACCESS_INDEX_READ_BIT | VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
    }
}

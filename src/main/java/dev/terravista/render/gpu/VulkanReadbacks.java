package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeArena;
import dev.terravista.render.DeviceBuffer;
import dev.terravista.render.ReadbackStream;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.vulkan.VkBufferCopy;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/** 协调原生下载记录与关闭，帧回调由读回帧队列管理。 */
public class VulkanReadbacks extends ReadbackStream {
    private final FrameCommands commands;
    private final GpuBuffer buffer;
    private final long pointer;
    private final ReadbackFrames results = new ReadbackFrames();
    private final Deque<ReadbackFrames.Data> pending = new ArrayDeque<>();
    private long current = -1;
    private long offset;

    public VulkanReadbacks(FrameCommands commands, long bytes) {
        this.commands = commands;
        buffer = new GpuBuffer(commands, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT, true);
        try { pointer = buffer.map(); }
        catch (RuntimeException | Error failure) { buffer.free(); throw failure; }
        results.arena.limit(bytes);
        commands.retirement().addListener(this::retired);
    }
    @Override public void download(DeviceBuffer source, long sourceOffset, long bytes, ResultConsumer consumer) {
        if (bytes > Integer.MAX_VALUE || bytes <= 0) throw new IllegalArgumentException();
        if (sourceOffset + bytes > source.sizeBytes()) throw new IllegalArgumentException();
        long address;
        if (current == -1 || !results.arena.grow(current, (int) bytes)) {
            current = results.arena.allocate((int) bytes);
            if (current == NativeArena.LIMIT_REACHED) {
                tick();
                commands.retirement().poll();
                current = results.arena.allocate((int) bytes);
            }
            if (current == NativeArena.LIMIT_REACHED) {
                ModLog.warn("Render: VK download stream full, force-idling the device to recover; this will hitch");
                commands.waitIdleAndRetire();
                current = results.arena.allocate((int) bytes);
                if (current == NativeArena.LIMIT_REACHED) {
                    throw new IllegalStateException("TerraVista Render: Could not allocate readback space even after device idle");
                }
            }
            results.allocations.add(current);
            offset = bytes;
            address = current;
        } else {
            address = current + offset;
            offset += bytes;
        }
        pending.add(new ReadbackFrames.Data((GpuBuffer) source, address, sourceOffset, bytes, consumer));
        commit();
    }
    @Override public void commit() {
        if (pending.isEmpty()) return;
        results.recordedFrame = commands.retirement().currentFrame();
        var command = commands.command();
        commands.barrier(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT);
        try (var stack = stackPush()) {
            var region = VkBufferCopy.calloc(1, stack);
            for (var copy : pending) {
                region.srcOffset(copy.targetOffset()).dstOffset(copy.streamOffset()).size(copy.bytes());
                vkCmdCopyBuffer(command, copy.target().buffer, buffer.buffer, region);
            }
        }
        commands.barrier(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_HOST_BIT, VK_ACCESS_HOST_READ_BIT);
        results.data.addAll(pending);
        pending.clear();
        current = -1;
        offset = 0;
    }
    @Override public void tick() { commit(); results.publish(); }
    private void retired(long inclusiveFrame) { results.retire(inclusiveFrame, pointer); }
    @Override public void waitDiscard() {
        boolean wasDiscarding = results.discarding;
        results.discarding = true;
        try { tick(); commands.waitIdleAndRetire(); }
        finally { results.discarding = wasDiscarding; }
    }
    @Override public void flushWaitClear() {
        tick();
        commands.waitIdleAndRetire();
        if (!results.framesEmpty()) throw new IllegalStateException("TerraVista Render: Cannot synchronously read back an unsubmitted Minecraft frame");
    }
    @Override public void free() { buffer.free(); }
}

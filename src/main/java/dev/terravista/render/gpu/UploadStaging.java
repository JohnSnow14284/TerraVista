package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeArena;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import static org.lwjgl.vulkan.VK10.*;

/** 在渲染线程管理持久上传区、分配器与按事件回收的内存。 */
final class UploadStaging {
    private record Frame(long index, NativeArena arena, LongArrayList allocations) {}
    private final FrameCommands commands;
    private GpuBuffer buffer;
    private long pointer;
    final int alignment;
    private NativeArena arena = new NativeArena();
    private final Deque<Long> retiredBanks = new ArrayDeque<>();
    private int liveBanks = 1;
    private static final int MAX_LIVE_BANKS = 4;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final LongArrayList allocations = new LongArrayList();
    private long current = -1;
    private long offset;

    UploadStaging(FrameCommands commands, long bytes) {
        this.commands = commands;
        buffer = createBuffer(bytes);
        try { pointer = buffer.map(); }
        catch (RuntimeException | Error failure) { buffer.free(); throw failure; }
        arena.limit(bytes);
        long minimum = Math.max(16, commands.device().storageAlignment());
        minimum = Math.max(minimum, commands.device().uniformAlignment());
        alignment = (int) minimum;
    }
    private GpuBuffer createBuffer(long bytes) {
        return new GpuBuffer(commands, bytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, true);
    }
    long handle() { return buffer.buffer; }
    long pointer() { return pointer; }
    void resetRange() { current = -1; offset = 0; }

    long allocateAligned(int bytes, Runnable publish) {
        if (bytes > buffer.size()) throw new IllegalArgumentException();
        long address;
        if (current == -1 || !arena.grow(current, bytes)) {
            current = arena.allocate(bytes);
            if (current == NativeArena.LIMIT_REACHED) {
                commands.retirement().poll();
                current = arena.allocate(bytes);
            }
            if (current == NativeArena.LIMIT_REACHED && liveBanks < MAX_LIVE_BANKS) {
                rotate(publish);
                current = arena.allocate(bytes);
            }
            if (current == NativeArena.LIMIT_REACHED) {
                ModLog.error("Render: VK upload stream full, force-idling the device to recover; this will hitch");
                publish.run();
                commands.waitIdleAndRetire();
                current = arena.allocate(bytes);
                if (current == NativeArena.LIMIT_REACHED) {
                    throw new IllegalStateException("TerraVista Render: Could not allocate upload staging space even after device idle");
                }
            }
            allocations.add(current);
            offset = bytes;
            address = current;
        } else {
            address = current + offset;
            offset += bytes;
        }
        if (current + bytes > buffer.size()) throw new IllegalStateException();
        return address;
    }
    private void rotate(Runnable publish) {
        publish.run();
        var next = createBuffer(buffer.size());
        long nextPointer;
        try { nextPointer = next.map(); }
        catch (RuntimeException | Error failure) { next.free(); throw failure; }
        buffer.free();
        retiredBanks.add(commands.retirement().currentFrame());
        liveBanks++;
        buffer = next;
        pointer = nextPointer;
        arena = new NativeArena();
        arena.limit(next.size());
    }
    void publishAllocations() {
        if (!allocations.isEmpty()) {
            frames.add(new Frame(commands.retirement().currentFrame(), arena, new LongArrayList(allocations)));
            allocations.clear();
        }
    }
    void retire(long inclusiveFrame) {
        while (!frames.isEmpty() && frames.peek().index <= inclusiveFrame) {
            var frame = frames.pop();
            frame.allocations.forEach(frame.arena::release);
        }
        while (!retiredBanks.isEmpty() && retiredBanks.peek() <= inclusiveFrame) {
            retiredBanks.pop();
            liveBanks--;
        }
    }
    void free() {
        retire(Long.MAX_VALUE);
        buffer.free();
    }
}

package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeArena;
import dev.terravista.render.ReadbackStream;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/** 按实际记录拷贝的帧编号管理内存与回调批次。 */
final class ReadbackFrames {
    record Data(GpuBuffer target, long streamOffset, long targetOffset, long bytes, ReadbackStream.ResultConsumer consumer) {}
    private record Frame(long index, LongArrayList allocations, ArrayList<Data> data) {}
    final NativeArena arena = new NativeArena();
    private final Deque<Frame> frames = new ArrayDeque<>();
    final LongArrayList allocations = new LongArrayList();
    final ArrayList<Data> data = new ArrayList<>();
    long recordedFrame = -1;
    boolean discarding;

    boolean framesEmpty() { return frames.isEmpty(); }

    void publish() {
        if (!allocations.isEmpty()) {
            frames.add(new Frame(recordedFrame, new LongArrayList(allocations), new ArrayList<>(data)));
            allocations.clear();
            data.clear();
        }
    }
    void retire(long inclusiveFrame, long base) {
        while (!frames.isEmpty() && frames.peek().index <= inclusiveFrame) {
            var frame = frames.pop();
            try {
                if (!discarding) {
                    for (var result : frame.data) {
                        try { result.consumer.consume(base + result.streamOffset, result.bytes); }
                        catch (RuntimeException failure) { ModLog.error("Render: Readback callback failed", failure); }
                    }
                }
            } finally {
                frame.allocations.forEach(arena::release);
            }
        }
    }
}

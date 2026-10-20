package dev.terravista.render;

import dev.terravista.memory.NativeMemory;
import java.util.Arrays;
import java.util.function.LongFunction;

/** 收集 Sodium 可见区块位置，仅向 GPU 发布变更前缀。 */
public final class VisibleBoundsBuffer {
    private static final int INITIAL_SECTIONS = 1 << 12;
    private final LongFunction<DeviceBuffer> allocator;
    private DeviceBuffer buffer;
    private int words;
    private boolean changed;
    private int uploadedWords;
    private int[] positions = new int[INITIAL_SECTIONS * 2];

    public VisibleBoundsBuffer(LongFunction<DeviceBuffer> allocator) {
        this.allocator = allocator;
        buffer = allocator.apply(INITIAL_SECTIONS * 8);
    }

    public void beginCollection() { words = 0; }

    public void append(long position) {
        if (words >= uploadedWords || positions[words] != (int) position
                || positions[words + 1] != (int) (position >>> 32)) changed = true;
        positions[words++] = (int) (position & 0xFFFFFFFFL);
        positions[words++] = (int) ((position >>> 32) & 0xFFFFFFFFL);
        if (words >= positions.length - 2) positions = Arrays.copyOf(positions, (int) (positions.length * 1.25));
    }

    public void prepare() {
        if (words == 0 || !changed) return;
        if (words * 4L > buffer.sizeBytes()) {
            var replacement = allocator.apply(((long) Math.ceil(words * 1.25)) * 4);
            buffer.free();
            buffer = replacement;
        }
        long pointer = UploadStream.current().upload(buffer, 0, words * 4);
        NativeMemory.copy(positions, words, pointer);
        UploadStream.current().commit();
        uploadedWords = words;
        changed = false;
    }

    public DeviceBuffer buffer() { return buffer; }
    public int count() { return words >> 1; }
    public void close() { buffer.free(); }
}

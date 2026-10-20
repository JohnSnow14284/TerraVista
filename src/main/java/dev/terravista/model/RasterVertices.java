package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

/** 持有光栅化所需的位置、元数据与纹理坐标流。 */
public final class RasterVertices {
    public static final int STRIDE = 24;
    private NativeBuffer buffer = new NativeBuffer(8192);
    private long cursor;
    private int count;
    private int defaultMetadata;
    private final int globalMetadata;
    private boolean shaded;
    private boolean darkened;
    private boolean discard;

    public RasterVertices() { this(0); }
    public RasterVertices(int globalMetadata) {
        reset();
        this.globalMetadata = globalMetadata;
    }
    public RasterVertices defaultMetadata(int metadata) { defaultMetadata = metadata; return this; }
    public int defaultMetadata() { return defaultMetadata; }
    public boolean shaded() { return shaded; }
    public boolean darkened() { return darkened; }
    public boolean discard() { return discard; }
    int globalMetadata() { return globalMetadata; }

    void markShaded(boolean value) { shaded |= value; }
    void markDarkened(boolean value) { darkened |= value; }
    void reserveQuad() { ensureSpace(); }

    public RasterVertices position(float x, float y, float z) {
        ensureSpace();
        cursor += STRIDE;
        count++;
        metadata(defaultMetadata | globalMetadata);
        MemoryUtil.memPutFloat(cursor, x);
        MemoryUtil.memPutFloat(cursor + 4, y);
        MemoryUtil.memPutFloat(cursor + 8, z);
        return this;
    }
    public RasterVertices metadata(int value) {
        discard |= (value & 1) != 0;
        MemoryUtil.memPutInt(cursor + 12, value);
        return this;
    }
    public RasterVertices uv(float u, float v) {
        MemoryUtil.memPutFloat(cursor + 16, u);
        MemoryUtil.memPutFloat(cursor + 20, v);
        return this;
    }
    private void ensureSpace() {
        if ((long) (count + 5) * STRIDE < buffer.bytes) return;
        long offset = cursor - buffer.address;
        var grown = new NativeBuffer((((int) (buffer.bytes * 2) + STRIDE - 1) / STRIDE) * STRIDE);
        buffer.copyTo(grown.address);
        buffer.free();
        buffer = grown;
        cursor = offset + grown.address;
    }
    public RasterVertices reset() {
        shaded = false;
        darkened = false;
        discard = false;
        defaultMetadata = 0;
        count = 0;
        cursor = buffer.address - STRIDE;
        return this;
    }
    public void free() {
        cursor = 0;
        count = 0;
        buffer.free();
        buffer = null;
    }
    public boolean empty() { return count == 0; }
    public int quadCount() {
        if (count % 4 != 0) throw new IllegalStateException();
        return count / 4;
    }
    public long address() { return buffer.address; }
}

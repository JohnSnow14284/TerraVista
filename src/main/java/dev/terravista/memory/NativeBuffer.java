package dev.terravista.memory;

import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 管理持有或借用的原生内存及其计数与释放。 */
public final class NativeBuffer extends TrackedResource {
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final AtomicLong BYTES = new AtomicLong();
    public final long address;
    public final long bytes;
    private final boolean owned;
    private final boolean counted;

    public NativeBuffer(long bytes) { this(true, MemoryUtil.nmemAlloc(bytes), bytes, true); }
    private NativeBuffer(boolean counted, long address, long bytes, boolean owned) {
        super(false);
        this.counted = counted; this.address = address; this.bytes = bytes; this.owned = owned;
        if (counted) COUNT.incrementAndGet();
        if (owned) BYTES.addAndGet(bytes);
    }
    public void copyTo(long target) { requireLive(); NativeMemory.copy(address, target, bytes); }
    public NativeBuffer copyFrom(long source) { requireLive(); NativeMemory.copy(source, address, bytes); return this; }
    private void retireAccounting() {
        if (counted) COUNT.decrementAndGet();
    }
    @Override public void free() {
        markReleased(); retireAccounting();
        if (!owned) throw new IllegalArgumentException("TerraVista Memory: Tried to free unfreeable buffer");
        MemoryUtil.nmemFree(address); BYTES.addAndGet(-bytes);
    }
    public NativeBuffer duplicate() {
        var result = new NativeBuffer(bytes); copyTo(result.address); return result;
    }
    public NativeBuffer resizeView(long size) {
        if (size > bytes || size <= 0) throw new IllegalArgumentException("TerraVista Memory: Requested size larger than current size, or less than 0, requested: " + size + " capacity: " + bytes);
        markReleased(); retireAccounting();
        if (owned) BYTES.addAndGet(-bytes);
        return new NativeBuffer(counted, address, size, owned);
    }
    public NativeBuffer zero() { MemoryUtil.memSet(address, 0, bytes); return this; }
    public ByteBuffer byteBuffer() { return MemoryUtil.memByteBuffer(address, (int) bytes); }
    public static NativeBuffer ownUntracked(long address, long bytes) { return new NativeBuffer(false, address, bytes, true); }
    public static NativeBuffer borrow(long address, long bytes) { return new NativeBuffer(false, address, bytes, false); }
    public NativeBuffer borrow() { return borrow(address, bytes); }
    public static int count() { return COUNT.get(); }
    public static long totalBytes() { return BYTES.get(); }
}

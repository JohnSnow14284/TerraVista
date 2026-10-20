package dev.terravista.world;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/** 保存持久化与驻留共享的区块数据。 */
public final class SectionContents {
    public static final int VOLUME = 32 * 32 * 32;
    private static final VarHandle CHILD_MASK;
    private static final VarHandle BLOCK_COUNT;
    static {
        try {
            CHILD_MASK = MethodHandles.lookup().findVarHandle(SectionContents.class, "childMask", byte.class);
            BLOCK_COUNT = MethodHandles.lookup().findVarHandle(SectionContents.class, "blockCount", int.class);
        } catch (NoSuchFieldException | IllegalAccessException exception) { throw new RuntimeException(exception); }
    }
    public final int level;
    public final int x;
    public final int y;
    public final int z;
    public final long key;
    private long[] blocks;
    private volatile byte childMask;
    private volatile int blockCount;

    public SectionContents(int level, int x, int y, int z, long[] blocks) {
        this.level = level; this.x = x; this.y = y; this.z = z;
        key = SectionCoordinates.key(level, x, y, z);
        this.blocks = blocks;
    }
    public long[] blocks() { return blocks; }
    void releaseBlocks() { blocks = null; }
    public static int index(int x, int y, int z) {
        final int mask = (1 << 5) - 1;
        return ((y & mask) << 10) | ((z & mask) << 5) | (x & mask);
    }
    public long replace(int x, int y, int z, long value) {
        int index = index(x, y, z);
        long previous = blocks[index];
        blocks[index] = value;
        return previous;
    }
    public long[] copy() { return Arrays.copyOf(blocks, blocks.length); }
    public void copyTo(long[] target, int offset) {
        if ((target.length - offset) < blocks.length) throw new IllegalArgumentException();
        System.arraycopy(blocks, 0, target, offset, blocks.length);
    }
    public static int childIndex(int x, int y, int z) { return (x & 1) | ((y & 1) << 2) | ((z & 1) << 1); }
    public byte childMask() { return (byte) CHILD_MASK.get(this); }
    public int blockCount() { return (int) BLOCK_COUNT.get(this); }
    public int addBlocks(int delta) {
        int count = ((int) BLOCK_COUNT.getAndAdd(this, delta)) + delta;
        return count;
    }
    public int updateChild(SectionContents child) {
        int childIndex = childIndex(child.x, child.y, child.z);
        byte mask = (byte) (1 << childIndex);
        byte previous, next;
        do {
            previous = childMask();
            next = (byte) ((previous & (~mask)) | (child.childMask() != 0 ? mask : 0));
        } while (!CHILD_MASK.compareAndSet(this, previous, next));
        return ((previous != 0) ^ (next != 0)) ? 2 : (previous != next ? 1 : 0);
    }
    public boolean updateLeafState() {
        byte previous, next;
        do {
            previous = childMask();
            next = (byte) (((int) BLOCK_COUNT.get(this)) == 0 ? 0 : 0xFF);
        } while (!CHILD_MASK.compareAndSet(this, previous, next));
        return previous != next;
    }
    /** 通过普通 VarHandle 写入设置值。 */
    public void setChildMask(byte mask) { CHILD_MASK.set(this, mask); }
    /** 反序列化时直接写入 volatile 字段。 */
    public void restoreChildMask(byte mask) { childMask = mask; }
    public void restoreBlockCount(int count) { blockCount = count; }
}

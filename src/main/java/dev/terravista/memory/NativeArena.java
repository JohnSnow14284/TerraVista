package dev.terravista.memory;

import it.unimi.dsi.fastutil.longs.LongRBTreeSet;

/** 管理上传与下载暂存内存的偏移分配。 */
public final class NativeArena {
    public static final long LIMIT_REACHED = -1;
    private static final int ADDRESS_BITS = 34;
    private static final int LENGTH_BITS = 64 - ADDRESS_BITS;
    private static final long LENGTH_MASK = (1L << LENGTH_BITS) - 1;
    private static final long ADDRESS_MASK = (1L << ADDRESS_BITS) - 1;
    private final LongRBTreeSet gaps = new LongRBTreeSet(Long::compareUnsigned);
    private final LongRBTreeSet allocations = new LongRBTreeSet(Long::compareUnsigned);
    private long ceiling = Long.MAX_VALUE;
    private long extent;
    private boolean changed;

    private static long live(long address, long length) { return (address << LENGTH_BITS) | length; }
    private static long gap(long address, long length) { return (length << ADDRESS_BITS) | address; }

    public void reset() {
        gaps.clear(); allocations.clear(); ceiling = Long.MAX_VALUE; extent = 0; changed = false;
    }
    public boolean takeResize() { boolean result = changed; changed = false; return result; }
    public long extent() { return extent; }
    public int gapCount() { return gaps.size(); }
    public int largestGap(int index) {
        var cursor = gaps.tailSet(-1).iterator();
        while (index > 0 && cursor.hasPrevious()) { cursor.previousLong(); index--; }
        return (int) (cursor.previousLong() >> ADDRESS_BITS);
    }

    public long allocate(int length) {
        if (length == 0) throw new IllegalArgumentException();
        var cursor = gaps.iterator(((long) length << ADDRESS_BITS) - 1);
        if (!cursor.hasNext()) {
            changed = true; // 先记录变更，再检查容量上限。
            long address = extent;
            if (extent + length > ceiling) return LIMIT_REACHED;
            extent += length;
            allocations.add(live(address, length));
            return address;
        }
        long record = cursor.nextLong();
        cursor.remove();
        if ((record >>> ADDRESS_BITS) == length) {
            allocations.add((record << LENGTH_BITS) | (record >>> ADDRESS_BITS));
        } else {
            allocations.add(live(record & ADDRESS_MASK, length));
            gaps.add(gap((record & ADDRESS_MASK) + length, (record >>> ADDRESS_BITS) - length));
        }
        return record & ADDRESS_MASK;
    }

    public int release(long address) {
        address &= ADDRESS_MASK;
        var cursor = allocations.iterator(address << LENGTH_BITS);
        long record = cursor.nextLong();
        if (record >> LENGTH_BITS != address) throw new IllegalStateException();
        long length = record & LENGTH_MASK;
        cursor.remove();
        if (cursor.hasPrevious()) {
            long previous = cursor.previousLong();
            long previousEnd = (previous >>> LENGTH_BITS) + (previous & LENGTH_MASK);
            if (previousEnd != address) {
                long distance = address - previousEnd;
                gaps.remove(gap(previousEnd, distance));
                record = live(previousEnd, (record & LENGTH_MASK) + distance);
            }
            cursor.nextLong();
        } else if (!gaps.isEmpty()) {
            // 以前导空隙的地址编码移除长度。
            if (gaps.remove(address << ADDRESS_BITS)) record = address + length;
        }
        if (cursor.hasNext()) {
            long next = cursor.nextLong();
            long end = (record >>> LENGTH_BITS) + (record & LENGTH_MASK);
            if (end != next >>> LENGTH_BITS) {
                long distance = (next >>> LENGTH_BITS) - end;
                gaps.remove(gap(end, distance));
                record = (record & (ADDRESS_MASK << LENGTH_BITS)) | ((record & LENGTH_MASK) + distance);
            }
        } else {
            changed = true;
            extent -= record & LENGTH_MASK;
            return (int) length;
        }
        gaps.add((record >>> LENGTH_BITS) | (record << ADDRESS_BITS));
        return (int) length;
    }

    public boolean grow(long address, int extra) {
        address &= ADDRESS_MASK;
        var cursor = allocations.iterator(address << LENGTH_BITS);
        if (!cursor.hasNext()) return false;
        long record = cursor.nextLong();
        if (record >> LENGTH_BITS != address) throw new IllegalStateException();
        long updated = (record & (ADDRESS_MASK << LENGTH_BITS)) | ((record & LENGTH_MASK) + extra);
        if (cursor.hasNext()) {
            long next = cursor.nextLong();
            long end = (record >>> LENGTH_BITS) + (record & LENGTH_MASK);
            long distance = (next >>> LENGTH_BITS) - end;
            if (extra > distance) return false;
            gaps.remove(gap(end, distance));
            // 移动两次迭代游标后移除当前分配。
            cursor.previousLong(); cursor.previousLong(); cursor.remove();
            allocations.add(updated);
            if (extra != distance) gaps.add(gap(end + extra, distance - extra));
            return true;
        }
        if (extent + extra > ceiling) return false;
        cursor.remove(); allocations.add(updated); extent += extra;
        // 扩展尾部容量时保留当前扩容标记。
        return true;
    }

    public long length(long address) {
        address &= ADDRESS_MASK;
        var cursor = allocations.iterator(address << LENGTH_BITS);
        if (!cursor.hasNext()) throw new IllegalArgumentException();
        long record = cursor.nextLong();
        if (record >> LENGTH_BITS != address) throw new IllegalStateException();
        return record & LENGTH_MASK;
    }
    public void limit(long bytes) {
        ceiling = bytes;
        if (ceiling < extent) throw new IllegalStateException("TerraVista Memory: Size set smaller than current size");
    }
    public long limit() { return ceiling; }
}

package dev.terravista.memory;

/** 通过四层六十四路占用索引分配记录槽位。 */
public final class FreeSlotIndex {
    public static final int FULL = -1;
    private static final int CAPACITY = 1 << 24;
    private final int limit;
    private int allocated;
    private int reportedEnd = -1;
    private long root;
    private final long[] groups = new long[64];
    private final long[] pages = new long[64 * 64];
    private final long[] slots = new long[64 * 64 * 64];

    public FreeSlotIndex() { this(CAPACITY); }
    public FreeSlotIndex(int limit) {
        this.limit = limit;
        if (limit > CAPACITY) throw new IllegalArgumentException("TerraVista Memory: Limit greater than capacity");
    }

    public int allocate() {
        if (root == -1 || allocated + 1 > limit) return FULL;
        int group = Long.numberOfTrailingZeros(~root);
        int page = 64 * group + Long.numberOfTrailingZeros(~groups[group]);
        int word = 64 * page + Long.numberOfTrailingZeros(~pages[page]);
        int index = 64 * word + Long.numberOfTrailingZeros(~slots[word]);
        occupy(index);
        return index;
    }

    private void occupy(int index) {
        reportedEnd += index == reportedEnd + 1 ? 1 : 0;
        int word = index >> 6;
        if ((slots[word] |= 1L << (index & 63)) == -1) {
            int page = word >> 6;
            if ((pages[page] |= 1L << (word & 63)) == -1) {
                int group = page >> 6;
                if ((groups[group] |= 1L << (page & 63)) == -1) {
                    root |= 1L << (group & 63);
                }
            }
        }
        allocated++;
    }

    private int nextAvailable(int index) {
        int bit;
        do {
            bit = Long.numberOfTrailingZeros((~root) & -(1L << (index >> 18)));
            index = Math.max(bit << 18, index);
            bit = Long.numberOfTrailingZeros((~groups[index >> 18]) & -(1L << ((index >> 12) & 63)));
            index = Math.max((bit + ((index >> 18) << 6)) << 12, index);
            if (bit == 64) continue;
            bit = Long.numberOfTrailingZeros((~pages[index >> 12]) & -(1L << ((index >> 6) & 63)));
            index = Math.max((bit + ((index >> 12) << 6)) << 6, index);
            if (bit == 64) continue;
            bit = Long.numberOfTrailingZeros((~slots[index >> 6]) & -(1L << (index & 63)));
            index = Math.max(bit + ((index >> 6) << 6), index);
        } while (bit == 64);
        return index;
    }

    public int allocateRun(int count) {
        if (count > 64) throw new IllegalStateException("TerraVista Memory: Fast-path allocation supports at most 64 slots");
        if (root == -1) return FULL;
        if (allocated + count >= limit) return -2;
        long mask = (1L << count) - 1;
        int first = nextAvailable(0);
        for (;;) {
            long occupied = slots[first >> 6] >>> (first & 63);
            if (64 - (first & 63) < count) occupied |= slots[(first >> 6) + 1] << (64 - (first & 63));
            if ((occupied & mask) != 0) {
                first = nextAvailable(first + Long.numberOfTrailingZeros(occupied));
                continue;
            }
            for (int offset = 0; offset < count; offset++) occupy(first + offset);
            return first;
        }
    }

    public boolean release(int index) {
        int word = index >> 6;
        long previous = slots[word];
        boolean wasAllocated = (previous & (1L << (index & 63))) != 0;
        allocated -= wasAllocated ? 1 : 0;
        if (wasAllocated && index == reportedEnd) {
            while (--reportedEnd >= 0 && !contains(reportedEnd)) { /* 越过已清空的槽位。 */ }
        }
        slots[word] = previous & ~(1L << (index & 63));
        int page = word >> 6;
        pages[page] &= ~(1L << (word & 63));
        int group = page >> 6;
        groups[group] &= ~(1L << (page & 63));
        root &= ~(1L << (group & 63));
        return wasAllocated;
    }

    public boolean contains(int index) { return (slots[index >> 6] & (1L << (index & 63))) != 0; }
    public int size() { return allocated; }
    public int limit() { return limit; }
    public int reportedEnd() { return reportedEnd; }
}

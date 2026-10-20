package dev.terravista.render.tree;

import dev.terravista.memory.FreeSlotIndex;

/** 保存 CPU 节点驻留状态与四字记录。 */
public final class NodeRecords {
    public static final int EMPTY_GEOMETRY_ID = -1;
    public static final int NODE_MASK = (1 << 24) - 1;
    public static final int REQUEST_MASK = (1 << 19) - 1;
    public static final int GEOMETRY_MASK = (1 << 24) - 1;
    public static final int MAX_GEOMETRY_ID = (1 << 24) - 3;
    private static final int WORDS = 4;
    private static final int GROWTH = 1 << 16;
    private final FreeSlotIndex slots;
    private long[] records;

    public NodeRecords(int limit) {
        if (limit >= NODE_MASK) throw new IllegalArgumentException("TerraVista Render: Max count too large");
        records = new long[GROWTH * WORDS];
        slots = new FreeSlotIndex(limit);
    }
    private static int offset(int node) { return node * WORDS; }
    private void ensureCapacity(int index) {
        if (index * WORDS >= records.length) {
            int capacity = Math.min(index + GROWTH, slots.limit());
            long[] expanded = new long[capacity * WORDS];
            System.arraycopy(records, 0, expanded, 0, records.length);
            records = expanded;
        }
    }
    private void clear(int node) {
        int at = offset(node);
        records[at] = -1;
        records[at + 1] = GEOMETRY_MASK | ((long) NODE_MASK << 24);
        records[at + 2] = REQUEST_MASK;
        records[at + 3] = 0;
    }
    public int allocate() {
        int node = slots.allocate();
        if (node < 0) throw new IllegalStateException("TerraVista Render: Failed to allocate node slot!");
        ensureCapacity(node);
        clear(node);
        return node;
    }
    public int allocate(int count) {
        if (count <= 0) throw new IllegalArgumentException("TerraVista Render: Count cannot be <= 0 was " + count);
        int base = slots.allocateRun(count);
        if (base < 0) throw new IllegalStateException("TerraVista Render: Could not allocate " + count + " consecutive tree nodes");
        ensureCapacity(base + count);
        for (int i = 0; i < count; i++) clear(base + i);
        return base;
    }
    public void release(int node) { release(node, 1); }
    public void release(int base, int count) {
        for (int i = 0; i < count; i++) {
            int node = base + i;
            if (!slots.release(node)) throw new IllegalStateException("TerraVista Render: Node " + node + " was not allocated!");
            clear(node);
        }
    }
    public void copy(int from, int to) {
        if (!(slots.contains(from) && slots.contains(to))) throw new IllegalArgumentException();
        int source = offset(from), target = offset(to);
        records[target] = records[source];
        records[target + 1] = records[source + 1];
        records[target + 2] = records[source + 2];
        records[target + 3] = records[source + 3];
    }
    public void position(int node, long position) { records[offset(node)] = position; }
    public long position(int node) { return records[offset(node)]; }
    public boolean contains(int node) { return slots.contains(node); }
    public int geometry(int node) {
        int value = (int) (records[offset(node) + 1] & GEOMETRY_MASK);
        if (value == GEOMETRY_MASK) return -1;
        if (value == GEOMETRY_MASK - 1) return -2;
        return value;
    }
    public void geometry(int node, int geometry) {
        if (geometry > MAX_GEOMETRY_ID)
            throw new IllegalArgumentException("TerraVista Render: Geometry ptr greater than MAX_GEOMETRY_ID: " + geometry);
        if (geometry == -1) geometry = GEOMETRY_MASK;
        if (geometry == -2) geometry = GEOMETRY_MASK - 1;
        if (geometry < 0) throw new IllegalArgumentException("TerraVista Render: Geometry ptr less than -1 : " + geometry);
        int at = offset(node) + 1;
        long data = records[at];
        data &= ~GEOMETRY_MASK;
        data |= geometry;
        records[at] = data;
    }
    public int childBase(int node) {
        int value = (int) ((records[offset(node) + 1] >> 24) & NODE_MASK);
        return value == NODE_MASK ? -1 : value;
    }
    public void childBase(int node, int base) {
        if (base >= NODE_MASK || base < -1)
            throw new IllegalArgumentException("TerraVista Render: Node child ptr greater GEQ NODE_ID_MSK or less than -1 : " + base);
        if (base == -1) base = NODE_MASK;
        int at = offset(node) + 1;
        long data = records[at];
        data &= ~((long) NODE_MASK << 24);
        data |= (long) base << 24;
        records[at] = data;
    }
    public void request(int node, int request) {
        if (request < 0 || request > REQUEST_MASK) throw new IllegalStateException("TerraVista Render: Too many requests to happen at once!");
        int at = offset(node) + 2;
        long data = records[at];
        data &= ~Integer.toUnsignedLong(REQUEST_MASK);
        data |= request;
        records[at] = data;
    }
    public int request(int node) { return (int) (records[offset(node) + 2] & REQUEST_MASK); }
    public void requestInFlight(int node, boolean state) {
        if (state) records[offset(node) + 1] |= 1L << 63;
        else records[offset(node) + 1] &= ~(1L << 63);
    }
    public boolean requestInFlight(int node) { return ((records[offset(node) + 1] >> 63) & 1) != 0; }
    public void childrenAreLeaves(int node, boolean state) {
        records[offset(node) + 2] &= ~(1L << 19);
        records[offset(node) + 2] |= state ? 1L << 19 : 0;
    }
    public boolean childrenAreLeaves(int node) { return ((records[offset(node) + 2] >> 19) & 1) != 0; }
    public void geometryInFlight(int node, boolean state) {
        if (state) records[offset(node) + 1] |= 1L << 59;
        else records[offset(node) + 1] &= ~(1L << 59);
    }
    public boolean geometryInFlight(int node) { return (records[offset(node) + 1] & (1L << 59)) != 0; }
    public int type(int node) { return (int) ((records[offset(node) + 1] >> 61) & 3) << 30; }
    public void type(int node, int type) {
        type >>>= 30;
        int at = offset(node) + 1;
        long data = records[at];
        data &= ~(3L << 61);
        data |= (long) type << 61;
        records[at] = data;
    }
    public byte childMask(int node) { return (byte) ((records[offset(node) + 1] >> 48) & 0xFF); }
    public void childMask(int node, byte mask) {
        int at = offset(node) + 1;
        long data = records[at];
        data &= ~(0xFFL << 48);
        data |= Byte.toUnsignedLong(mask) << 48;
        records[at] = data;
    }
    public int childCount(int node) { return (int) ((records[offset(node) + 1] >> 56) & 7) + 1; }
    public void childCount(int node, int count) {
        if (count <= 0 || count > 8) throw new IllegalArgumentException("TerraVista Render: Count: " + count);
        int at = offset(node) + 1;
        long data = records[at];
        data &= ~(7L << 56);
        data |= (long) (count - 1) << 56;
        records[at] = data;
    }
    public void write(long address, int node) { NodeWireFormat.write(this, address, node); }
    public int reportedEnd() { return slots.reportedEnd(); }
    public int size() { return slots.size(); }
}

package dev.terravista.memory;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

/** 通过可复用槽位管理对象，按 0.75 比例扩容。 */
public final class IndexedObjects<T> {
    private final Int2ObjectFunction<T[]> arrays;
    private final FreeSlotIndex occupancy;
    private T[] values;

    public IndexedObjects(Int2ObjectFunction<T[]> arrays) { this(arrays, -1); }
    public IndexedObjects(Int2ObjectFunction<T[]> arrays, int limit) {
        this.arrays = arrays;
        values = arrays.apply(16);
        occupancy = limit == -1 ? new FreeSlotIndex() : new FreeSlotIndex(limit);
    }
    public int add(T value) {
        int index = occupancy.allocate();
        if (index < 0) throw new IllegalStateException("TerraVista Memory: Id over max request capacity");
        if (values.length <= index) {
            int capacity = values.length + (int) Math.ceil(values.length * 0.75f);
            T[] grown = arrays.apply(capacity);
            System.arraycopy(values, 0, grown, 0, values.length);
            values = grown;
        }
        values[index] = value;
        return index;
    }
    public void release(int index) {
        if (!occupancy.release(index)) throw new IllegalArgumentException("TerraVista Memory: Index " + index + " was already released");
        values[index] = null;
    }
    public T get(int index) {
        if (!occupancy.contains(index)) throw new IllegalArgumentException("TerraVista Memory: Index " + index + " is not allocated");
        return values[index];
    }
    public int size() { return occupancy.size(); }
}

package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.lwjgl.system.MemoryUtil;

/** 封装根、几何、散布记录与可见性的一次完整发布。 */
final class TreeDelta {
    int lastNode;
    final IntOpenHashSet roots = new IntOpenHashSet();
    int sections;
    long usedBytes;
    final GeometryCopyBatch geometry = new GeometryCopyBatch();
    NativeBuffer scatter = new NativeBuffer(8192 * 2);
    final Int2IntOpenHashMap scatterLocations = new Int2IntOpenHashMap(1024);
    { scatterLocations.defaultReturnValue(-1); }
    final IntOpenHashSet cleaner = new IntOpenHashSet();

    void reset() {
        cleaner.clear();
        scatterLocations.clear();
        lastNode = 0;
        roots.clear();
        sections = 0;
        usedBytes = 0;
        geometry.reset();
    }
    long scatterAddress(int location) { return scatterAddress(location, 0); }
    long scatterAddress(int location, int extra) {
        int offset = scatterLocations.get(location);
        if (offset != -1) return scatter.address + 16L * offset;
        ensureScatter(1 + extra);
        int index = scatterLocations.size();
        int chunk = (index / 4) * 5, slot = index & 3;
        MemoryUtil.memPutInt(scatter.address + chunk * 16L + slot * 4L, location);
        int dataOffset = chunk + 1 + slot;
        scatterLocations.put(location, dataOffset);
        return scatter.address + dataOffset * 16L;
    }
    private void ensureScatter(int extra) {
        int chunks = (scatterLocations.size() + extra + 3) / 4;
        long required = chunks * 5L * 16L;
        if (scatter.bytes <= required) {
            long capacity = (long) (scatter.bytes * 1.5 + extra * 80L);
            capacity = ((capacity + 79) / 80) * 80;
            ModLog.info("Render: Expanding scatter update buffer to " + capacity);
            var expanded = new NativeBuffer(capacity);
            scatter.copyTo(expanded.address);
            scatter.free();
            scatter = expanded;
        }
    }
    void close() {
        geometry.close();
        scatter.free();
    }
}

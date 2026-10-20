package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeArena;
import dev.terravista.memory.NativeBuffer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.system.MemoryUtil;

/** 跨树发布批次持有紧凑拷贝头与可复用 CPU 暂存数据。 */
final class GeometryCopyBatch {
    int elements;
    int maxElement;
    NativeBuffer headers = new NativeBuffer(1 << 16);
    NativeBuffer data = new NativeBuffer(1 << 20);
    final NativeArena arena = new NativeArena();
    final Int2IntOpenHashMap destinations = new Int2IntOpenHashMap();
    { destinations.defaultReturnValue(-1); }

    void remove(int destination) {
        int header = destinations.remove(destination);
        if (header == -1) return;
        long at = headers.address + header * 16L;
        int size = MemoryUtil.memGetInt(at + 8);
        elements -= size;
        if (arena.release(MemoryUtil.memGetInt(at)) != size)
            throw new IllegalStateException("TerraVista Render: Freed memory not same size as expected");
        if (MemoryUtil.memGetInt(at + 4) != destination)
            throw new IllegalStateException("TerraVista Render: Destination not the same as point");
        if (header == destinations.size()) {
            MemoryUtil.memPutLong(at, 0);
            MemoryUtil.memPutLong(at + 8, 0);
            return;
        }
        moveLastHeader(header);
    }
    private void moveLastHeader(int target) {
        long source = headers.address + destinations.size() * 16L;
        int destination = MemoryUtil.memGetInt(source + 4);
        if (destinations.get(destination) != destinations.size())
            throw new IllegalStateException("TerraVista Render: ending header not pointing at end point");
        long at = headers.address + target * 16L;
        MemoryUtil.memPutLong(at, MemoryUtil.memGetLong(source));
        MemoryUtil.memPutLong(source, 0);
        MemoryUtil.memPutLong(at + 8, MemoryUtil.memGetLong(source + 8));
        MemoryUtil.memPutLong(source + 8, 0);
        destinations.put(destination, target);
    }
    void upload(int destination, NativeBuffer incoming) {
        if (incoming.bytes % 8 != 0) throw new IllegalStateException("TerraVista Render: Data must be of size multiple 8");
        int size = (int) (incoming.bytes / 8);
        maxElement = Math.max(maxElement, destination + size);
        int header = destinations.get(destination);
        if (header != -1) replace(destination, header, size, incoming);
        else append(destination, size, incoming);
    }
    private void replace(int destination, int header, int size, NativeBuffer incoming) {
        long at = headers.address + header * 16L;
        if (MemoryUtil.memGetInt(at + 4) != destination)
            throw new IllegalStateException("TerraVista Render: Existing destination not the point");
        int previous = MemoryUtil.memGetInt(at + 8);
        if (previous == size) incoming.copyTo(data.address + MemoryUtil.memGetInt(at) * 8L);
        else {
            if (arena.release(MemoryUtil.memGetInt(at)) != previous)
                throw new IllegalStateException("TerraVista Render: Freed allocation not size as expected");
            elements -= previous;
            elements += size;
            int offset = allocateData(size);
            incoming.copyTo(data.address + offset * 8L);
            MemoryUtil.memPutInt(at, offset);
            MemoryUtil.memPutInt(at + 8, size);
        }
    }
    private void append(int destination, int size, NativeBuffer incoming) {
        int header = destinations.size();
        destinations.put(destination, header);
        if (headers.bytes <= header * 16L) {
            long capacity = Math.max(headers.bytes * 2, header * 16L);
            ModLog.info("Render: Resizing scratch header buffer to: " + capacity);
            var expanded = new NativeBuffer(capacity);
            headers.copyTo(expanded.address);
            headers.free();
            headers = expanded;
        }
        long at = headers.address + header * 16L;
        elements += size;
        int offset = allocateData(size);
        incoming.copyTo(data.address + offset * 8L);
        MemoryUtil.memPutInt(at, offset);
        MemoryUtil.memPutInt(at + 4, destination);
        MemoryUtil.memPutInt(at + 8, size);
    }
    private int allocateData(int size) {
        int offset = (int) arena.allocate(size);
        if (data.bytes <= (offset + size) * 8L) {
            long capacity = Math.max(data.bytes * 2, (offset + size) * 8L);
            ModLog.info("Render: Resizing scratch data buffer to: " + capacity);
            var expanded = new NativeBuffer(capacity);
            data.copyTo(expanded.address);
            data.free();
            data = expanded;
        }
        return offset;
    }
    void reset() {
        maxElement = 0;
        elements = 0;
        destinations.clear();
        arena.reset();
    }
    void close() {
        headers.free();
        headers = null;
        data.free();
        data = null;
    }
}

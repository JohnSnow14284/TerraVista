package dev.terravista.storage.backend;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** 在锁保护下复制调色板并复位输入游标。 */
final class MemoryPalette {
    private final Int2ObjectMap<ByteBuffer> entries = new Int2ObjectOpenHashMap<>();
    void put(int id, ByteBuffer data) {
        synchronized (entries) {
            var copy = MemoryUtil.memAlloc(data.remaining());
            MemoryUtil.memCopy(data, copy);
            var previous = entries.put(id, copy);
            if (previous != null) MemoryUtil.memFree(previous);
        }
    }
    Int2ObjectOpenHashMap<byte[]> snapshot() {
        var result = new Int2ObjectOpenHashMap<byte[]>();
        synchronized (entries) {
            for (var entry : entries.int2ObjectEntrySet()) {
                var data = new byte[entry.getValue().remaining()];
                entry.getValue().get(data);
                entry.getValue().rewind();
                result.put(entry.getIntKey(), data);
            }
        }
        return result;
    }
    void release() { entries.values().forEach(MemoryUtil::memFree); }
}

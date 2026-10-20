package dev.terravista.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;

/** 定义字节后端与区块存储共享的调色板持久化操作。 */
public interface PaletteStorage {
    void putIdMapping(int id, ByteBuffer data);
    Int2ObjectOpenHashMap<byte[]> getIdMappingsData();
    void flush();
    void close();
}

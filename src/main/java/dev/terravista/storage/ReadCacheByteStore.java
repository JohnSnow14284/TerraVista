package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;

/** 写入缓存并在未命中时读取底层存储。 */
public final class ReadCacheByteStore extends ByteStore {
    private final ByteStore cache;
    private final ByteStore source;
    public ReadCacheByteStore(ByteStore cache, ByteStore source) { this.cache = cache; this.source = source; }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) {
        var data = cache.read(key, scratch);
        if (data != null) return data;
        data = source.read(key, scratch);
        if (data != null) cache.put(key, data);
        return data;
    }
    @Override public void put(long key, NativeBuffer data) { cache.put(key, data); }
    @Override public void delete(long key) { cache.delete(key); }
    @Override public void putIdMapping(int id, ByteBuffer data) { cache.putIdMapping(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return source.getIdMappingsData(); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { throw new IllegalStateException("TerraVista Storage: Position enumeration is unavailable for the LMDB backend"); }
    @Override public void flush() { cache.close(); source.close(); }
    @Override public void close() { cache.close(); source.close(); }
    public static final class Config extends ByteStoreConfig {
        public ByteStoreConfig cache;
        public ByteStoreConfig onMiss;
        @Override public List<ByteStoreConfig> children() { return List.of(cache, onMiss); }
        @Override public ByteStore build(StorageBuildContext context) { return new ReadCacheByteStore(cache.build(context), onMiss.build(context)); }
        public static String getConfigTypeName() { return "ReadonlyCachingLayer"; }
    }
}

package dev.terravista.storage.backend;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.ByteStore;
import dev.terravista.storage.ByteStoreConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.apache.commons.lang3.stream.Streams;

/** 分片保存原生区块副本并独立管理调色板锁。 */
public final class MemoryByteStore extends ByteStore {
    private final Long2ObjectMap<NativeBuffer>[] buckets;
    private final MemoryPalette palette = new MemoryPalette();
    public MemoryByteStore() { this(4); }
    @SuppressWarnings("unchecked")
    public MemoryByteStore(int sliceBits) {
        buckets = new Long2ObjectMap[1 << sliceBits];
        for (int i = 0; i < buckets.length; i++) buckets[i] = new Long2ObjectOpenHashMap<>();
    }
    private Long2ObjectMap<NativeBuffer> bucket(long key) {
        return buckets[(int) (RandomSupport.mixStafford13(RandomSupport.mixStafford13(key) ^ key) & (buckets.length - 1))];
    }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) {
        var bucket = bucket(key);
        synchronized (bucket) {
            var data = bucket.get(key);
            if (data == null) return null;
            data.copyTo(scratch.address);
            return scratch.resizeView(data.bytes);
        }
    }
    @Override public void put(long key, NativeBuffer data) {
        var bucket = bucket(key);
        synchronized (bucket) {
            var previous = bucket.put(key, data.duplicate());
            if (previous != null) previous.free();
        }
    }
    @Override public void delete(long key) {
        var bucket = bucket(key);
        synchronized (bucket) { var data = bucket.remove(key); if (data != null) data.free(); }
    }
    @Override public void iteratePositions(int level, LongConsumer consumer) {
        LongConsumer filtered = consumer;
        if (level != -1) filtered = key -> { if ((int) ((key >> 60) & 0xf) == level) consumer.accept(key); };
        for (var bucket : buckets) synchronized (bucket) { bucket.keySet().forEach(filtered); }
    }
    @Override public void putIdMapping(int id, ByteBuffer data) { palette.put(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return palette.snapshot(); }
    @Override public void close() {
        Streams.of(buckets).map(Long2ObjectMap::values).flatMap(ObjectCollection::stream).forEach(NativeBuffer::free);
        palette.release();
    }
    public static final class Config extends ByteStoreConfig {
        @Override public ByteStore build(StorageBuildContext context) { return new MemoryByteStore(); }
        public static String getConfigTypeName() { return "Memory"; }
    }
}

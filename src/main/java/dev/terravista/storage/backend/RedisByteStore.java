package dev.terravista.storage.backend;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.ByteStore;
import dev.terravista.storage.ByteStoreConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

/** 通过连接池管理两个持久化哈希表。 */
public final class RedisByteStore extends ByteStore {
    private final RedisConnections connections;
    private final RedisSectionHash sections;
    private final RedisPaletteHash palette;
    public RedisByteStore(String host, int port, String prefix) { this(host, port, prefix, null, null); }
    public RedisByteStore(String host, int port, String prefix, String user, String password) {
        connections = new RedisConnections(host, port, prefix, user, password);
        sections = new RedisSectionHash(connections); palette = new RedisPaletteHash(connections);
    }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) { return sections.read(key, scratch); }
    @Override public void put(long key, NativeBuffer data) { sections.put(key, data); }
    @Override public void delete(long key) { sections.delete(key); }
    @Override public void putIdMapping(int id, ByteBuffer data) { palette.put(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return palette.snapshot(); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { throw new IllegalStateException("TerraVista Storage: Position enumeration is unavailable for the LMDB backend"); }
    @Override public void close() { connections.close(); }
    public static final class Config extends ByteStoreConfig {
        public String host;
        public int port;
        public String prefix;
        @Override public ByteStore build(StorageBuildContext context) { return new RedisByteStore(host, port, context.substitute(prefix)); }
        public static String getConfigTypeName() { return "Redis"; }
    }
}

package dev.terravista.storage.backend;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.ByteStore;
import dev.terravista.storage.ByteStoreConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

/** 将区块、调色板表的构造与生命周期连接到数据库。 */
public final class RocksByteStore extends ByteStore {
    private final RocksDatabase database;
    private final RocksSectionTable sections;
    private final RocksPaletteTable palette;
    public RocksByteStore(String path) { database = new RocksDatabase(path); sections = new RocksSectionTable(database); palette = new RocksPaletteTable(database); }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) { return sections.read(key, scratch); }
    @Override public void put(long key, NativeBuffer data) { sections.put(key, data); }
    @Override public void delete(long key) { sections.delete(key); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { sections.visit(level, consumer); }
    @Override public void putIdMapping(int id, ByteBuffer data) { palette.put(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return palette.snapshot(); }
    @Override public void flush() { database.flush(); }
    @Override public void close() { database.close(); }
    public static final class Config extends ByteStoreConfig {
        @Override public ByteStore build(StorageBuildContext context) { return new RocksByteStore(context.ensureDirectory(context.substitute(context.path()))); }
        public static String getConfigTypeName() { return "RocksDB"; }
    }
}

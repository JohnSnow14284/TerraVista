package dev.terravista.storage.backend;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.ByteStore;
import dev.terravista.storage.ByteStoreConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

/** 协调环境、事务与独立区块和调色板表的生命周期。 */
public final class LmdbByteStore extends ByteStore {
    private static final long GROW_BYTES = 1 << 25;
    private final LmdbAccessGate access = new LmdbAccessGate(this::grow);
    private final LmdbEnvironment environment;
    private final LmdbSectionTable sections;
    private final LmdbPaletteTable palette;
    public LmdbByteStore(String directory) {
        environment = new LmdbEnvironment.Builder().maxDatabases(2).open(directory, 0).build();
        environment.mapSize(GROW_BYTES);
        sections = new LmdbSectionTable(environment);
        palette = new LmdbPaletteTable(environment);
    }
    private void grow() {
        long bytes = environment.mapSize() + GROW_BYTES;
        ModLog.info("Storage: Growing DBI env size to: " + bytes + " bytes");
        environment.mapSize(bytes);
    }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) { return access.access(() -> sections.read(key, scratch)); }
    @Override public void put(long key, NativeBuffer data) { access.growing(() -> { sections.put(key, data); return null; }); }
    @Override public void delete(long key) { access.access(() -> { sections.delete(key); return null; }); }
    @Override public synchronized void putIdMapping(int id, ByteBuffer data) { access.growing(() -> { palette.put(id, data); return null; }); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return access.access(palette::snapshot); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { throw new IllegalStateException("TerraVista Storage: Position enumeration is unavailable for the LMDB backend"); }
    @Override public void flush() { environment.flush(true); }
    @Override public void close() { sections.close(); palette.close(); environment.close(); }
    public static final class Config extends ByteStoreConfig {
        @Override public ByteStore build(StorageBuildContext context) { return new LmdbByteStore(context.ensureDirectory(context.substitute(context.path()))); }
        public static String getConfigTypeName() { return "LMDB"; }
    }
}

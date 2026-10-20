package dev.terravista.storage;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.ResizableNativeScratch;
import dev.terravista.world.SectionContents;
import dev.terravista.world.VoxelBits;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongConsumer;

/** 连接字节存储、区块编解码与加载结果处理。 */
public final class SectionPersistence extends SectionStore {
    private static final ResizableNativeScratch READ_SCRATCH = new ResizableNativeScratch(SectionFormat.MAX_SERIALIZED_BYTES + 1024);
    private final ByteStore backend;
    public SectionPersistence(ByteStore backend) { this.backend = backend; }
    @Override public int load(SectionContents section) {
        try {
            var data = backend.read(section.key, READ_SCRATCH.get().borrow());
            if (data == null) return 1;
            if (SectionCodec.decode(section, data)) return 0;
        } catch (CorruptSectionException exception) {
            ModLog.error("Storage: Section decompression failed; stored bytes retained", exception);
        }
        Arrays.fill(section.blocks(), VoxelBits.AIR);
        section.restoreChildMask((byte) 0);
        section.restoreBlockCount(0);
        ModLog.error("Storage: Cannot decode section " + section.level + ", " + section.x + ", " + section.y + ", " + section.z + "; stored bytes retained");
        return -1;
    }
    @Override public void save(SectionContents section) {
        var data = SectionCodec.encode(section);
        backend.put(section.key, data);
    }
    @Override public void putIdMapping(int id, ByteBuffer data) { backend.putIdMapping(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return backend.getIdMappingsData(); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { backend.iteratePositions(level, consumer); }
    @Override public void flush() { backend.flush(); }
    @Override public void close() { backend.close(); }
    public static final class Config extends SectionStoreConfig {
        public ByteStoreConfig storage;
        @Override public SectionStore build(StorageBuildContext context) { return new SectionPersistence(storage.build(context)); }
        public static String getConfigTypeName() { return "Serializer"; }
    }
}

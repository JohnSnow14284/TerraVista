package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import net.minecraft.world.level.levelgen.RandomSupport;

/** 按区块键选择分片并顺序广播调色板与生命周期操作。 */
public final class FragmentedByteStore extends ByteStore {
    private final ByteStore[] fragments;
    public FragmentedByteStore(ByteStore... fragments) {
        this.fragments = fragments;
        int count = fragments.length;
        if ((count & (count - 1)) != 0) throw new IllegalArgumentException("TerraVista Storage: Backend count not a power of 2");
    }
    private int fragment(long key) {
        return (int) (RandomSupport.mixStafford13(RandomSupport.mixStafford13(key) ^ key) & (fragments.length - 1));
    }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) { return fragments[fragment(key)].read(key, scratch); }
    @Override public void put(long key, NativeBuffer data) { fragments[fragment(key)].put(key, data); }
    @Override public void delete(long key) { fragments[fragment(key)].delete(key); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { for (var store : fragments) store.iteratePositions(level, consumer); }
    @Override public void putIdMapping(int id, ByteBuffer data) { for (var store : fragments) store.putIdMapping(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return PaletteConsensus.select(fragments); }
    @Override public void flush() { for (var store : fragments) store.flush(); }
    @Override public void close() { for (var store : fragments) store.close(); }
    @Override public List<ByteStore> children() { return List.of(fragments); }
    public static final class Config extends ByteStoreConfig {
        public List<ByteStoreConfig> backends = new ArrayList<>();
        @Override public List<ByteStoreConfig> children() { return new ArrayList<>(backends); }
        @Override public ByteStore build(StorageBuildContext context) {
            var stores = new ByteStore[backends.size()];
            for (int i = 0; i < stores.length; i++) stores[i] = backends.get(i).build(context);
            return new FragmentedByteStore(stores);
        }
        public static String getConfigTypeName() { return "FragmentationAdaptor"; }
    }
}

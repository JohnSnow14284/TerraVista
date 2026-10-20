package dev.terravista.world;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import dev.terravista.storage.PaletteStorage;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 按顺序发布运行时编号，持久化与 NBT 分别管理。 */
public final class StatePalette {
    public static final long UNKNOWN_MAPPING = -1;
    final ReentrantLock blockLock = new ReentrantLock();
    final ConcurrentHashMap<BlockState, BlockPaletteEntry> blocks = new ConcurrentHashMap<>(2000, 0.75f, 10);
    final ObjectArrayList<BlockPaletteEntry> blockIds = new ObjectArrayList<>();
    final ReentrantLock biomeLock = new ReentrantLock();
    final ConcurrentHashMap<String, BiomePaletteEntry> biomes = new ConcurrentHashMap<>(2000, 0.75f, 10);
    final ObjectArrayList<BiomePaletteEntry> biomeIds = new ObjectArrayList<>();
    private final PalettePersistence persistence;
    private Consumer<BlockPaletteEntry> blockAdded;
    private Consumer<BiomePaletteEntry> biomeAdded;

    public StatePalette(PaletteStorage storage) {
        persistence = new PalettePersistence(storage);
        var air = new BlockPaletteEntry(0, Blocks.AIR.defaultBlockState());
        blocks.put(air.state, air); blockIds.add(air);
        persistence.restore(this);
    }
    public void onBlock(Consumer<BlockPaletteEntry> callback) { blockAdded = callback; }
    public void onBiome(Consumer<BiomePaletteEntry> callback) { biomeAdded = callback; }
    public int blockCount() { return blockIds.size(); }
    private BlockPaletteEntry registerBlock(BlockState state) {
        BlockPaletteEntry entry;
        blockLock.lock();
        try {
            entry = blocks.get(state);
            if (entry != null) return entry;
            entry = new BlockPaletteEntry(blockIds.size(), state);
            persistence.save(entry);
            blockIds.add(entry); blocks.put(state, entry);
        } finally { blockLock.unlock(); }
        if (blockAdded != null) blockAdded.accept(entry);
        return entry;
    }
    private BiomePaletteEntry registerBiome(String biome) {
        BiomePaletteEntry entry;
        biomeLock.lock();
        try {
            entry = biomes.get(biome);
            if (entry != null) return entry;
            entry = new BiomePaletteEntry(biomeIds.size(), biome);
            persistence.save(entry);
            biomeIds.add(entry); biomes.put(biome, entry);
        } finally { biomeLock.unlock(); }
        if (biomeAdded != null) biomeAdded.accept(entry);
        return entry;
    }
    public long baseId(byte light, BlockState state, Holder<Biome> biome) {
        if (state.isAir()) return Byte.toUnsignedLong(light) << 56;
        return VoxelBits.compose(light, blockId(state), biomeId(biome));
    }
    public BlockState blockState(int id) { return blockIds.get(id).state; }
    public int blockId(BlockState state) {
        if (state.isAir()) return 0;
        var entry = blocks.get(state);
        if (entry == null) entry = registerBlock(state);
        return entry.id;
    }
    int mipTraits(int id) { return blockIds.get(id).mipTraits; }
    int mipHeight(int id) { return blockIds.get(id).mipHeight; }
    public int biomeId(Holder<Biome> biome) {
        String name = biome.unwrapKey().get().identifier().toString();
        var entry = biomes.get(name);
        if (entry == null) entry = registerBiome(name);
        return entry.id;
    }
    public BlockPaletteEntry[] blockEntries() {
        blockLock.lock();
        try {
            var entries = new ArrayList<>(blockIds);
            var result = new BlockPaletteEntry[entries.size()]; int index = 0;
            for (var entry : entries) {
                if (entry.id != index++) throw new IllegalStateException();
                result[index - 1] = entry;
            }
            return result;
        } finally { blockLock.unlock(); }
    }
    public BiomePaletteEntry[] biomeEntries() {
        biomeLock.lock();
        try {
            var entries = new ArrayList<>(biomeIds);
            var result = new BiomePaletteEntry[entries.size()]; int index = 0;
            for (var entry : entries) {
                if (entry.id != index++) throw new IllegalStateException();
                result[index - 1] = entry;
            }
            return result;
        } finally { biomeLock.unlock(); }
    }
    public void resaveAll() {
        var blockSnapshot = new ArrayList<>(blocks.values());
        var biomeSnapshot = new ArrayList<>(biomes.values());
        for (var entry : blockSnapshot) {
            if (entry.state.isAir() && entry.id == 0) continue;
            if (blockIds.indexOf(entry) != entry.id)
                throw new IllegalStateException("TerraVista World: Block-state palette index mismatch; array=" + blockIds.indexOf(entry) + " entry=" + entry.id);
            persistence.save(entry);
        }
        for (var entry : biomeSnapshot) {
            if (biomeIds.indexOf(entry) != entry.id)
                throw new IllegalStateException("TerraVista World: Biome palette index mismatch");
            persistence.save(entry);
        }
        persistence.flush();
    }
}

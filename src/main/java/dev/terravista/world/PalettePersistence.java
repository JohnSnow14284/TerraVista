package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.storage.PaletteStorage;
import java.util.ArrayList;
import java.util.Comparator;
import org.lwjgl.system.MemoryUtil;

/** 按顺序恢复类型映射键并管理原生写入内存。 */
final class PalettePersistence {
    private static final int BLOCK_TYPE = 1;
    private static final int BIOME_TYPE = 2;
    private final PaletteStorage storage;
    PalettePersistence(PaletteStorage storage) { this.storage = storage; }
    void save(BlockPaletteEntry entry) { write(entry.id | (BLOCK_TYPE << 30), entry.encode()); }
    void save(BiomePaletteEntry entry) { write(entry.id | (BIOME_TYPE << 30), entry.encode()); }
    private void write(int id, byte[] bytes) {
        var buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes); buffer.rewind();
            storage.putIdMapping(id, buffer);
        } finally { MemoryUtil.memFree(buffer); }
    }
    void flush() { storage.flush(); }
    void restore(StatePalette palette) {
        var mappings = storage.getIdMappingsData();
        var blockEntries = new ArrayList<BlockPaletteEntry>();
        var biomeEntries = new ArrayList<BiomePaletteEntry>();
        boolean[] resave = new boolean[1];
        for (var entry : mappings.int2ObjectEntrySet()) {
            int type = entry.getIntKey() >>> 30;
            int id = entry.getIntKey() & ((1 << 30) - 1);
            if (type == BLOCK_TYPE) {
                var block = BlockPaletteEntry.decode(id, entry.getValue(), resave);
                if (block.state.isAir()) {
                    ModLog.error("World: Unavailable block mapping " + id + "; keeping its slot as air and retaining stored data");
                    blockEntries.add(block); continue;
                }
                blockEntries.add(block);
                var previous = palette.blocks.putIfAbsent(block.state, block);
                if (previous != null)
                    ModLog.warn("World: Duplicate block-state mappings; retaining the existing entry: " + previous.id + ":" + block.id + ":" + block.state);
            } else if (type == BIOME_TYPE) {
                var biome = BiomePaletteEntry.decode(id, entry.getValue());
                biomeEntries.add(biome);
                if (palette.biomes.put(biome.biome, biome) != null)
                    throw new IllegalStateException("TerraVista World: Multiple mappings for biome entry");
            } else throw new IllegalStateException("TerraVista World: Unknown entryType");
        }
        blockEntries.stream().sorted(Comparator.comparing(entry -> entry.id)).forEach(entry -> {
            if (palette.blockIds.size() != entry.id) throw new IllegalStateException("TerraVista World: Block entry not ordered");
            palette.blockIds.add(entry);
        });
        biomeEntries.stream().sorted(Comparator.comparing(entry -> entry.id)).forEach(entry -> {
            if (palette.biomeIds.size() != entry.id)
                throw new IllegalStateException("TerraVista World: Biome entry not ordered. got " + entry.biome + " with id " + entry.id + " expected id " + palette.biomeIds.size());
            palette.biomeIds.add(entry);
        });
        if (resave[0]) { ModLog.warn("World: Forced state resave triggered"); palette.resaveAll(); }
    }
}

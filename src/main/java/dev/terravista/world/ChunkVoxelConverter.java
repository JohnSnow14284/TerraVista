package dev.terravista.world;

import dev.terravista.bootstrap.RuntimeEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

/** 按四千零九十六方块顺序采样区块数据与光照。 */
public final class ChunkVoxelConverter {
    private static final boolean LITHIUM = RuntimeEnvironment.modLoaded("lithium");
    private static final ThreadLocal<ConversionScratch> SCRATCH = ThreadLocal.withInitial(ConversionScratch::new);
    private ChunkVoxelConverter() {}
    public static ChunkVoxels convert(ChunkVoxels section, StatePalette world, PalettedContainer<BlockState> blocks,
                                      PalettedContainerRO<Holder<Biome>> biomeSource, VoxelLighting light) {
        var scratch = SCRATCH.get();
        var stateCache = scratch.states(world);
        var biomes = scratch.biomes;
        var data = section.values;
        var sourcePalette = blocks.data.palette();
        var palette = scratch.palette(sourcePalette.getSize());
        GlobalPalette<BlockState> global = null;
        int last;
        if (blocks.data.palette() instanceof GlobalPalette<BlockState> found) {
            global = found; last = global.getSize();
        } else {
            last = ChunkPaletteDecoder.fill(sourcePalette, stateCache, world, palette, LITHIUM);
            last = Math.max(0, last - 1);
        }
        int index = 0;
        for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
            int biome = world.biomeId(biomeSource.get(x, y, z));
            biomes[index++] = biome;
        }
        int nonAir = 0;
        if (blocks.data.storage() instanceof SimpleBitStorage storage) {
            var words = storage.getRaw();
            int perWord = (64 / storage.getBits()) - 1;
            int mask = (1 << storage.getBits()) - 1;
            int bits = storage.getBits();
            long sample = 0;
            int word = 0, remaining = 0;
            for (int cell = 0; cell <= 0xFFF; cell++) {
                if (remaining-- == 0) { sample = words[word++]; remaining = perWord; }
                int block = global == null ? palette[Math.min((int) (sample & mask), last)] : world.blockId(global.valueFor((int) (sample & mask)));
                sample >>>= bits;
                byte lighting = light.sample(cell & 0xF, (cell >> 8) & 0xF, (cell >> 4) & 0xF);
                nonAir += block != 0 ? 1 : 0;
                data[cell] = VoxelBits.compose(lighting, block, biomes[Integer.compress(cell, 0b1100_1100_1100)]);
            }
        } else {
            if (!(blocks.data.storage() instanceof ZeroBitStorage)) throw new IllegalStateException();
            int block = palette[0];
            if (block == 0) {
                for (int cell = 0; cell <= 0xFFF; cell++) data[cell] = VoxelBits.airWithLight(light.sample(cell & 0xF, (cell >> 8) & 0xF, (cell >> 4) & 0xF));
            } else {
                nonAir = 4096;
                for (int cell = 0; cell <= 0xFFF; cell++) {
                    byte lighting = light.sample(cell & 0xF, (cell >> 8) & 0xF, (cell >> 4) & 0xF);
                    data[cell] = VoxelBits.compose(lighting, block, biomes[Integer.compress(cell, 0b1100_1100_1100)]);
                }
            }
        }
        section.nonAirCount = nonAir;
        return section;
    }
}

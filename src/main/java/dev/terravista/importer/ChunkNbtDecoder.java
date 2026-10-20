package dev.terravista.importer;

import com.mojang.serialization.Codec;
import dev.terravista.world.ChunkLodBuilder;
import dev.terravista.world.ChunkVoxelConverter;
import dev.terravista.world.ChunkVoxels;
import dev.terravista.world.VoxelWorld;
import dev.terravista.world.WorldChunkUpdates;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/** 解码区域节的方块、群系与光照，并写入多级体素。 */
final class ChunkNbtDecoder {
    private static final byte[] EMPTY = new byte[0];
    private static final ThreadLocal<ChunkVoxels> SCRATCH = ThreadLocal.withInitial(ChunkVoxels::empty);
    private final VoxelWorld world;
    private final Codec<PalettedContainer<BlockState>> blocks;
    private final Codec<PalettedContainerRO<Holder<Biome>>> biomes;
    private final PalettedContainerRO<Holder<Biome>> fallback;

    ChunkNbtDecoder(VoxelWorld world, Level level) {
        this.world = world;
        var plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var factory = PalettedContainerFactory.create(level.registryAccess());
        fallback = new PalettedContainer<>(plains, factory.biomeStrategy());
        biomes = factory.biomeContainerCodec();
        blocks = factory.blockStatesContainerCodec();
    }

    void section(int x, int y, int z, CompoundTag section) {
        if (section.getCompound("block_states").isEmpty()) return;
        byte[] blockBytes = section.getByteArray("BlockLight").orElse(EMPTY);
        byte[] skyBytes = section.getByteArray("SkyLight").orElse(EMPTY);
        DataLayer blockLight = blockBytes.length == 0 ? null : new DataLayer(blockBytes);
        DataLayer skyLight = skyBytes.length == 0 ? null : new DataLayer(skyBytes);
        var decoded = blocks.parse(NbtOps.INSTANCE, section.getCompound("block_states").get());
        if (!decoded.hasResultOrPartial()) return;
        var blockStates = decoded.getPartialOrThrow();
        var biomeSource = fallback;
        var encodedBiomes = section.getCompound("biomes");
        if (encodedBiomes.isPresent()) {
            biomeSource = biomes.parse(NbtOps.INSTANCE, encodedBiomes.get()).result().orElse(fallback);
        }
        var voxels = ChunkVoxelConverter.convert(SCRATCH.get().position(x, y, z), world.palette(),
                blockStates, biomeSource, (bx, by, bz) -> {
                    int block = blockLight == null ? 0 : blockLight.get(bx, by, bz);
                    int sky = skyLight == null ? 0 : skyLight.get(bx, by, bz);
                    return (byte) (sky | (block << 4));
                });
        ChunkLodBuilder.build(voxels, world.palette());
        WorldChunkUpdates.apply(world, voxels);
    }
}

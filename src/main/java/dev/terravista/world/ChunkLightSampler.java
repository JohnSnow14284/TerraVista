package dev.terravista.world;

import net.minecraft.world.level.chunk.DataLayer;

/** 为每次摄取作业选择方块光与天空光采样路径。 */
public final class ChunkLightSampler {
    private ChunkLightSampler() {}

    public static VoxelLighting from(DataLayer block, DataLayer sky) {
        boolean hasSky = sky != null && !sky.isEmpty();
        boolean hasBlock = block != null && !block.isEmpty();
        if (hasSky && hasBlock) return (x, y, z) -> {
            int blockValue = Math.min(15, block.get(x, y, z));
            int skyValue = Math.min(15, sky.get(x, y, z));
            return (byte) (skyValue | (blockValue << 4));
        };
        if (hasBlock) return (x, y, z) -> (byte) (Math.min(15, block.get(x, y, z)) << 4);
        if (hasSky) return (x, y, z) -> (byte) Math.min(15, sky.get(x, y, z));
        return (x, y, z) -> (byte) 0;
    }
}

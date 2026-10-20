package dev.terravista.world;

/** 同步插入区块，传播父级存在标记并按顺序发布变更。 */
public final class WorldChunkUpdates {
    private WorldChunkUpdates() {}

    /** 返回后允许调用方复用区块数组。 */
    public static void apply(VoxelWorld world, ChunkVoxels chunk) {
        if (!world.live) throw new IllegalStateException("TerraVista World: World is not live");
        boolean propagateExistence = false;
        ResidentSection child = null;
        try {
            for (int level = 0; level <= VoxelWorld.MAX_LEVEL; level++) {
                var current = world.acquire(level, chunk.x >> (level + 1), chunk.y >> (level + 1), chunk.z >> (level + 1));
                boolean retained = false;
                try {
                    int existenceChange = 0;
                    if (level != 0 && propagateExistence) {
                        existenceChange = current.contents().updateChild(child.contents());
                        var previous = child;
                        child = null;
                        previous.release();
                    }
                    long placement = SectionVoxelWriter.write(chunk, current.contents());
                    boolean valuesChanged = (placement & 1) == 1;
                    int previousNonAir = (int) ((placement >> 1) & 0x1FFF);
                    if (level == 0) {
                        int delta = chunk.nonAirCount - previousNonAir;
                        if (delta != 0) {
                            current.contents().addBlocks(delta);
                            existenceChange = current.contents().updateLeafState() ? 2 : 0;
                        }
                    }
                    if (valuesChanged || existenceChange != 0) {
                        int neighbors = valuesChanged ? boundaryNeighbors(chunk, level) : 0;
                        world.changed(current, (valuesChanged ? VoxelWorld.BLOCKS_CHANGED : 0)
                                | (existenceChange != 0 ? VoxelWorld.CHILDREN_CHANGED : 0), neighbors);
                    }
                    if (valuesChanged || existenceChange == 2) {
                        if (existenceChange == 2) {
                            propagateExistence = true;
                            child = current;
                            retained = true;
                        } else {
                            propagateExistence = false;
                            child = null;
                        }
                    } else {
                        propagateExistence = false;
                        child = null;
                    }
                } finally { if (!retained) current.release(); }
            }
        } finally { if (child != null) child.release(); }
    }

    private static int boundaryNeighbors(ChunkVoxels chunk, int level) {
        int neighbors = 0;
        neighbors |= ((chunk.y ^ (chunk.y - 1)) >> (level + 1)) == 0 ? 0 : 1 << 0;
        neighbors |= ((chunk.y ^ (chunk.y + 1)) >> (level + 1)) == 0 ? 0 : 1 << 1;
        neighbors |= ((chunk.x ^ (chunk.x - 1)) >> (level + 1)) == 0 ? 0 : 1 << 2;
        neighbors |= ((chunk.x ^ (chunk.x + 1)) >> (level + 1)) == 0 ? 0 : 1 << 3;
        neighbors |= ((chunk.z ^ (chunk.z - 1)) >> (level + 1)) == 0 ? 0 : 1 << 4;
        neighbors |= ((chunk.z ^ (chunk.z + 1)) >> (level + 1)) == 0 ? 0 : 1 << 5;
        return neighbors;
    }
}

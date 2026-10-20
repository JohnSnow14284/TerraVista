package dev.terravista.world;

/** 将区块 LOD 写入驻留区块对应八分体，等待统一发布。 */
public final class SectionVoxelWriter {
    private SectionVoxelWriter() {}

    /** 以最低位标记变更，以第一至十三位保存旧零级非空气数量。 */
    public static long write(ChunkVoxels chunk, SectionContents destination) {
        final long[] source = chunk.values;
        final int level = destination.level;
        final int coordinateMask = (1 << (level + 1)) - 1;
        final int x = (chunk.x & coordinateMask) << (4 - level);
        final int y = (chunk.y & coordinateMask) << (4 - level);
        final int z = (chunk.z & coordinateMask) << (4 - level);
        int previousNonAir = 0;
        boolean changed = false;
        var target = destination.blocks();
        int origin = x | (z << 5) | (y << 10);
        if (level == 0) {
            final int rowMask = 0b1100 | (0xf << 5) | (0xf << 10);
            final int step = (~rowMask) + 1;
            int offset = 0;
            for (int cell = 0; cell <= 0xFFF; cell += 4) {
                int index = offset + origin;
                offset = (offset + step) & rowMask;
                long old0 = target[index]; target[index] = source[cell];
                long old1 = target[index + 1]; target[index + 1] = source[cell + 1];
                long old2 = target[index + 2]; target[index + 2] = source[cell + 2];
                long old3 = target[index + 3]; target[index + 3] = source[cell + 3];
                previousNonAir += VoxelBits.nonAir(old0); changed |= source[cell] != old0;
                previousNonAir += VoxelBits.nonAir(old1); changed |= source[cell + 1] != old1;
                previousNonAir += VoxelBits.nonAir(old2); changed |= source[cell + 2] != old2;
                previousNonAir += VoxelBits.nonAir(old3); changed |= source[cell + 3] != old3;
            }
        } else {
            int start = ChunkVoxels.levelOffset(level);
            int rowMask = 0xF >> level;
            rowMask |= (rowMask << 5) | (rowMask << 10);
            int step = (~rowMask) + 1;
            int offset = 0;
            for (int cell = start; cell <= (0xFFF >> (level * 3)) + start; cell++) {
                int index = offset + origin;
                offset = (offset + step) & rowMask;
                long value = source[cell];
                long old = target[index];
                changed |= value != old;
                target[index] = value;
            }
        }
        return (changed ? 1 : 0) | (Integer.toUnsignedLong(previousNonAir) << 1);
    }
}

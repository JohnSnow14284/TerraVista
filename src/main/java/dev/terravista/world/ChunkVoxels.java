package dev.terravista.world;

import java.util.Arrays;

/** 保存十六立方区块与随后四级打包的 LOD。 */
public final class ChunkVoxels {
    public int x;
    public int y;
    public int z;
    public int nonAirCount;
    public final long[] values;
    public ChunkVoxels(long[] values) { this.values = values; }
    public static int levelOffset(int level) {
        int offset = level == 1 ? (1 << 12) : 0;
        offset |= level == 2 ? (1 << 12) | (1 << 9) : 0;
        offset |= level == 3 ? (1 << 12) | (1 << 9) | (1 << 6) : 0;
        offset |= level == 4 ? (1 << 12) | (1 << 9) | (1 << 6) | (1 << 3) : 0;
        return offset;
    }
    public ChunkVoxels position(int x, int y, int z) { this.x = x; this.y = y; this.z = z; return this; }
    public long get(int level, int x, int y, int z) {
        int size = 4 - level;
        int mask = (1 << size) - 1;
        x = (x >> 0) & mask; y = (y >> 0) & mask; z = (z >> 0) & mask;
        return values[((y << (size << 1)) | (z << size) | x) + levelOffset(level)];
    }
    public static ChunkVoxels empty() { return new ChunkVoxels(new long[16 * 16 * 16 + 8 * 8 * 8 + 4 * 4 * 4 + 2 * 2 * 2 + 1]); }
    public ChunkVoxels clear() { nonAirCount = 0; Arrays.fill(values, 0); return this; }
}

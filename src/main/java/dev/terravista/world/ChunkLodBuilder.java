package dev.terravista.world;

/** 按遍历与角点顺序原地构建四级 LOD。 */
public final class ChunkLodBuilder {
    private ChunkLodBuilder() {}
    public static void build(ChunkVoxels section, StatePalette palette) {
        var data = section.values;
        for (int cell = 0; cell < 4096; cell++) data[cell] |= 512;
        int index = 0;
        int mask = 0b1110_1110_1110;
        int step = (~mask) + 1;
        int source = 0;
        while (true) {
            data[4096 + index++] = VoxelMip.combine(
                    data[source | 0], data[source | 1], data[source | 16], data[source | 17],
                    data[source | 256], data[source | 257], data[source | 272], data[source | 273], palette);
            if (source == mask) break;
            source = (source + step) & mask;
        }
        for (int level = 2; level <= 4; level++) {
            int side = 16 >> (level - 1);
            int input = ChunkVoxels.levelOffset(level - 1);
            int output = ChunkVoxels.levelOffset(level);
            index = 0;
            for (int y = 0; y < side; y += 2) {
                for (int z = 0; z < side; z += 2) {
                    for (int x = 0; x < side; x += 2) {
                        int base = input + (y * side + z) * side + x;
                        data[output + index++] = VoxelMip.combine(
                                data[base], data[base + 1], data[base + side], data[base + side + 1],
                                data[base + side * side], data[base + side * side + 1],
                                data[base + side * side + side], data[base + side * side + side + 1], palette);
                    }
                }
            }
        }
    }
}

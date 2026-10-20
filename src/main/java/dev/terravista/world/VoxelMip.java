package dev.terravista.world;

/** 按占用比例、流体表层和材质分布选择父体素。 */
public final class VoxelMip {
    private static final ThreadLocal<Cube> CUBES = ThreadLocal.withInitial(Cube::new);
    private static final long BLOCK_MASK = ((1L << 20) - 1) << 27;
    private VoxelMip() {}

    public static long combine(long v000, long v100, long v001, long v101,
                               long v010, long v110, long v011, long v111, StatePalette palette) {
        var cube = CUBES.get();
        long[] values = cube.values;
        values[0] = v000; values[1] = v100; values[2] = v001; values[3] = v101;
        values[4] = v010; values[5] = v110; values[6] = v011; values[7] = v111;
        long differences = 0;
        int blockLight = 0, skyLight = 0;
        boolean sampled = true;
        for (long value : values) {
            sampled &= VoxelBits.sampled(value);
            differences |= value ^ v000;
            int light = VoxelBits.light(value);
            blockLight += light & 240;
            skyLight = Math.max(skyLight, light & 15);
        }
        int airLight = (blockLight / 8 & 240) | skyLight;
        if ((differences & BLOCK_MASK) == 0) {
            if ((palette.mipTraits(VoxelBits.block(v000)) & 64) != 0) return VoxelBits.sampledAir(airLight, sampled);
            return surface(cube, values[representative(cube, -1, 1)], palette);
        }
        int kinds = 0, occupied = 0, fluidY = -1;
        for (int i = 0; i < 8; i++) {
            int block = VoxelBits.block(values[i]), traits = palette.mipTraits(block);
            if ((traits & 64) != 0) { cube.kind[i] = -1; continue; }
            occupied++;
            int kind = 0;
            while (kind < kinds && cube.blocks[kind] != block) kind++;
            if (kind == kinds) {
                cube.blocks[kind] = block; cube.traits[kind] = traits;
                cube.count[kind] = 0; cube.upper[kind] = 0; cube.fluidScore[kind] = 0; cube.exposed[kind] = 0;
                kinds++;
            }
            cube.kind[i] = kind; cube.count[kind]++;
            cube.upper[kind] = Math.max(cube.upper[kind], i >> 2);
            if ((traits & 16) != 0) fluidY = Math.max(fluidY, i >> 2);
        }
        for (int i = 0; i < 4; i++) {
            int upper = cube.kind[i + 4], lower = cube.kind[i];
            int kind = upper >= 0 && (cube.traits[upper] & 128) != 0 ? upper
                    : lower >= 0 && (cube.traits[lower] & 128) != 0 ? lower : upper >= 0 ? upper : lower;
            if (kind >= 0) cube.exposed[kind]++;
        }
        if (fluidY >= 0) {
            boolean covered = false;
            int solidScore = 0;
            for (int i = 0; i < 8; i++) {
                int kind = cube.kind[i];
                if (kind < 0) continue;
                int traits = cube.traits[kind], y = i >> 2;
                if (y > fluidY && (traits & 128) != 0 && (traits & 16) == 0) { covered = true; break; }
                if (y != fluidY) continue;
                if ((traits & 16) != 0) cube.fluidScore[kind] += (traits & 32) != 0 ? 8 : 6;
                else if ((traits & 128) != 0) solidScore += 6;
            }
            if (!covered) {
                int best = 0;
                for (int kind = 1; kind < kinds; kind++)
                    if (cube.fluidScore[kind] > cube.fluidScore[best]) best = kind;
                if (cube.fluidScore[best] > 0 && cube.fluidScore[best] >= solidScore)
                    return surface(cube, values[representative(cube, best, fluidY)], palette);
            }
        }
        // 少数占用向空气取整，半数占用保留薄层。
        if (occupied >= 4) {
            int best = 0, bestScore = -1;
            for (int kind = 0; kind < kinds; kind++) {
                int traits = cube.traits[kind];
                int score = (cube.exposed[kind] << 12) + (cube.count[kind] << 8) + ((traits & 15) << 3) + (cube.upper[kind] << 2)
                        + ((traits & 16) != 0 ? 2 : 0) + ((traits & 32) != 0 ? 1 : 0);
                if (score > bestScore) { best = kind; bestScore = score; }
            }
            return surface(cube, values[representative(cube, best, cube.upper[best])], palette);
        }
        return VoxelBits.sampledAir(airLight, sampled);
    }

    private static long surface(Cube cube, long selected, StatePalette palette) {
        int total = 0, top = 0;
        int selectedTraits = palette.mipTraits(VoxelBits.block(selected));
        boolean fluid = (selectedTraits & 16) != 0;
        boolean ice = (selectedTraits & 256) != 0;
        for (int column = 0; column < 4; column++) {
            int columnTop = 0;
            for (int y = 0; y < 2; y++) {
                long value = cube.values[column + y * 4];
                int block = VoxelBits.block(value), traits = palette.mipTraits(block);
                if ((traits & 64) != 0 || fluid && (traits & 16) == 0 || ice && (traits & 256) == 0
                        || (selectedTraits & 128) != 0 && (traits & 128) == 0) continue;
                int height = VoxelBits.surfaceHeight(value);
                if (height == 0) height = (traits & 32) != 0 ? 256 : palette.mipHeight(block);
                columnTop = Math.max(columnTop, y * 256 + height);
            }
            total += columnTop;
            top = Math.max(top, columnTop);
        }
        // 地形按列平均，液面与冰盖保留所在的水平面。
        int height = fluid || ice ? (top + 1) / 2 : (total + 4) / 8;
        boolean waterBelow = false;
        if (ice) for (int i = 0; i < 8; i++) {
            long value = cube.values[i];
            waterBelow |= VoxelBits.iceWater(value) || (i >> 2) < ((top - 1) >> 8)
                    && (palette.mipTraits(VoxelBits.block(value)) & 512) != 0;
        }
        selected = VoxelBits.withIceWater(selected, waterBelow);
        return VoxelBits.withSurfaceHeight(selected, Math.max(1, height));
    }

    private static int representative(Cube cube, int kind, int y) {
        int best = 0, bestScore = -1;
        for (int i = 0; i < 8; i++) {
            if (kind >= 0 && cube.kind[i] != kind) continue;
            int score = ((i >> 2) == y ? 256 : 0) + VoxelBits.light(cube.values[i]);
            if (score > bestScore) { best = i; bestScore = score; }
        }
        return best;
    }

    private static final class Cube {
        final long[] values = new long[8];
        final int[] kind = new int[8], blocks = new int[8], traits = new int[8], count = new int[8],
                upper = new int[8], fluidScore = new int[8], exposed = new int[8];
    }
}

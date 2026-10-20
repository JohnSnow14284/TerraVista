package dev.terravista.render.tree;

import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;

/** 计算父子地址并校验各层树坐标。 */
final class TreeCoordinates {
    private TreeCoordinates() {}
    static void validate(long position) {
        int level = SectionCoordinates.level(position);
        int x = SectionCoordinates.x(position), y = SectionCoordinates.y(position), z = SectionCoordinates.z(position);
        if (SectionCoordinates.key(level, x, y, z) != position)
            throw new IllegalStateException("TerraVista Render: Reconstructed pos not same as original");
        x <<= level; y <<= level; z <<= level;
        long base = SectionCoordinates.key(0, x, y, z);
        if (SectionCoordinates.level(base) != 0 || SectionCoordinates.x(base) != x
                || SectionCoordinates.y(base) != y || SectionCoordinates.z(base) != z)
            throw new IllegalStateException("TerraVista Render: Position not valid at all levels: " + position + "-"
                    + SectionCoordinates.describe(position) + ":" + SectionCoordinates.describe(base));
    }
    static int childIndex(long position) {
        int x = SectionCoordinates.x(position), y = SectionCoordinates.y(position), z = SectionCoordinates.z(position);
        return (x & 1) | ((y & 1) << 2) | ((z & 1) << 1);
    }
    static long child(long position, int index) {
        int level = SectionCoordinates.level(position);
        if (level == 0) throw new IllegalArgumentException("TerraVista Render: Cannot create a child lower than lod level 0");
        return SectionCoordinates.key(level - 1, (SectionCoordinates.x(position) << 1) | (index & 1),
                (SectionCoordinates.y(position) << 1) | ((index >> 2) & 1),
                (SectionCoordinates.z(position) << 1) | ((index >> 1) & 1));
    }
    static long parent(long position) {
        int level = SectionCoordinates.level(position);
        if (level == VoxelWorld.MAX_LEVEL)
            throw new IllegalArgumentException("TerraVista Render: Cannot create a parent higher than LoD " + VoxelWorld.MAX_LEVEL);
        return SectionCoordinates.key(level + 1, SectionCoordinates.x(position) >> 1,
                SectionCoordinates.y(position) >> 1, SectionCoordinates.z(position) >> 1);
    }
}

package dev.terravista.render.tree;

import dev.terravista.world.SectionCoordinates;

/** 连接树变化所需的订阅，并使用统一坐标键格式。 */
public interface SectionWatch {
    default boolean watch(int level, int x, int y, int z, int types) {
        return watch(SectionCoordinates.key(level, x, y, z), types);
    }
    boolean watch(long position, int types);
    default boolean unwatch(int level, int x, int y, int z, int types) {
        return unwatch(SectionCoordinates.key(level, x, y, z), types);
    }
    boolean unwatch(long position, int types);
    default int get(int level, int x, int y, int z) { return get(SectionCoordinates.key(level, x, y, z)); }
    int get(long position);
}

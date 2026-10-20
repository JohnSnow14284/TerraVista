package dev.terravista.world;

/** 读取指定局部坐标的天空光与方块光。 */
@FunctionalInterface
public interface VoxelLighting {
    byte sample(int x, int y, int z);
}

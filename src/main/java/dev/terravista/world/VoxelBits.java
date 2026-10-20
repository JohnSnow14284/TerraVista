package dev.terravista.world;

/** 打包方块、生物群系与光照，并按输入位写入替换值。 */
public final class VoxelBits {
    public static final long AIR = 0;
    private VoxelBits() {}
    public static boolean isAir(long value) { return (value & (((1L << 20) - 1) << 27)) == 0; }
    public static int nonAir(long value) { return Math.min(block(value), 1); }
    public static int block(long value) { return (int) ((value >> 27) & ((1 << 20) - 1)); }
    public static int biome(long value) { return (int) ((value >> 47) & 0x1FF); }
    public static int light(long value) { return (int) ((value >> 56) & 0xFF); }
    public static int surfaceHeight(long value) { return (int) (value & 511); }
    public static long withSurfaceHeight(long value, int height) { return (value & ~511L) | height; }
    public static boolean iceWater(long value) { return (value & 1024) != 0; }
    public static long withIceWater(long value, boolean water) { return (value & ~1024L) | (water ? 1024 : 0); }
    public static boolean sampled(long value) { return (value & 512) != 0 || !isAir(value); }
    public static long sampledAir(int light, boolean sampled) { return airWithLight(light) | (sampled ? 512 : 0); }
    public static long withLight(long value, int light) { return (value & (~(0xFFL << 56))) | (Integer.toUnsignedLong(light & 0xFF) << 56); }
    public static long withBlockBiome(long value, int block, int biome) { return (value & (0xFFL << 56)) | (Integer.toUnsignedLong(block) << 27) | (Integer.toUnsignedLong(biome) << 47); }
    public static long airWithLight(int light) { return Integer.toUnsignedLong(light & 0xFF) << 56; }
    public static long compose(byte light, int block, int biome) {
        if (block == AIR) return Byte.toUnsignedLong(light) << 56;
        return (Byte.toUnsignedLong(light) << 56) | (Integer.toUnsignedLong(biome) << 47) | (Integer.toUnsignedLong(block) << 27);
    }
}

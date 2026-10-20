package dev.terravista.world;

/** 编码区块地址并保留低四位供调用方使用。 */
public final class SectionCoordinates {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_LEVEL = 4;
    private SectionCoordinates() {}
    public static long key(int level, int x, int y, int z) {
        return ((long) level << 60) | ((long) (y & 0xFF) << 52) | ((long) (z & ((1 << 24) - 1)) << 28) | ((long) (x & ((1 << 24) - 1)) << 4);
    }
    public static int level(long key) { return (int) ((key >> 60) & 0xf); }
    public static int x(long key) { return (int) ((key << 36) >> 40); }
    public static int y(long key) { return (int) ((key << 4) >> 56); }
    public static int z(long key) { return (int) ((key << 12) >> 40); }
    public static String describe(long key) { return level(key) + "@[" + x(key) + ", " + y(key) + ", " + z(key) + "]"; }
}

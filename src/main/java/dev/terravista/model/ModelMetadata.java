package dev.terravista.model;

/** 将六个面字节、全局标记与光照打包为网格元数据。 */
public final class ModelMetadata {
    private ModelMetadata() {}
    public static boolean faceExists(long metadata, int face) { return ((metadata >> (8 * face)) & 0xFF) != 0xFF; }
    public static boolean faceCanBeOccluded(long metadata, int face) { return ((metadata >> (8 * face)) & 4) == 4; }
    public static boolean faceOccludes(long metadata, int face) { return faceExists(metadata, face) && ((metadata >> (8 * face)) & 1) == 1; }
    public static boolean faceUsesSelfLighting(long metadata, int face) { return ((metadata >> (8 * face)) & 8) != 0; }
    public static boolean doubleSided(long metadata) { return ((metadata >> (8 * 6)) & 4) != 0; }
    public static long doubleSidedBit(long metadata) { return (metadata >> (8 * 6 + 2)) & 1L; }
    public static boolean translucent(long metadata) { return ((metadata >> (8 * 6)) & 2) != 0; }
    public static long translucentBit(long metadata) { return (metadata >> (8 * 6 + 1)) & 1L; }
    public static boolean containsFluid(long metadata) { return ((metadata >> (8 * 6)) & 8) != 0; }
    public static long containsFluidBit(long metadata) { return (metadata >> (8 * 6 + 3)) & 1L; }
    public static boolean fluid(long metadata) { return ((metadata >> (8 * 6)) & 16) != 0; }
    public static long fluidBit(long metadata) { return (metadata >> (8 * 6 + 4)) & 1L; }
    public static boolean biomeColoured(long metadata) { return ((metadata >> (8 * 6)) & 1L) != 0; }
    public static long biomeColouredBit(long metadata) { return (metadata >> (8 * 6)) & 1L; }
    public static long notBiomeColouredBit(long metadata) { return ((~metadata) >> (8 * 6)) & 1L; }
    public static boolean cullsSame(long metadata) { return ((metadata >> (8 * 6)) & 32) != 0; }
    public static boolean fullyOpaque(long metadata) { return ((metadata >> (8 * 6)) & 64) != 0; }
    public static long fullyOpaqueBit(long metadata) { return (metadata >> (8 * 6 + 6)) & 1L; }
    public static long lightEmission(long metadata) { return (metadata >> (8 * 6 + 7)) & 0xFL; }
}

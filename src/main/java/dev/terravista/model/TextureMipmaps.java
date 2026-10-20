package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

/** 按六面图集布局写入四级 mip。 */
public final class TextureMipmaps {
    public static final int FACE_SIZE = 16;
    public static final int LAYERS = Integer.numberOfTrailingZeros(FACE_SIZE);
    private TextureMipmaps() {}

    static long allocationBytes() { return (2L * 3 * pixelCountWithMips(FACE_SIZE)) * 4; }
    static int pixelCountWithMips(int size) {
        int total = 0;
        for (; size != 0; size >>= 1) total += size * size;
        return total;
    }

    public static void write(boolean darkened, ModelFacePixels[] faces, NativeBuffer target) {
        long base = target.address;
        byte transparentFaces = pack(faces, base);
        if (!darkened) TransparentColourFill.fill(base, transparentFaces);
        downsample(darkened, base);
    }

    private static byte pack(ModelFacePixels[] faces, long base) {
        byte transparentFaces = 0;
        for (int face = 0; face < 6; face++) {
            int x = (face >> 1) * FACE_SIZE;
            int y = (face & 1) * FACE_SIZE;
            int index = 0;
            boolean transparent = false;
            for (int colour : faces[face].pixels()) {
                int offset = ((y + (index >> LAYERS)) * (FACE_SIZE * 3)
                        + ((index & (FACE_SIZE - 1)) + x)) * 4;
                index++;
                MemoryUtil.memPutInt(base + offset, colour);
                transparent |= (colour & 0xFF000000) == 0;
            }
            transparentFaces |= (transparent ? 1 : 0) << face;
        }
        return transparentFaces;
    }

    private static void downsample(boolean darkened, long destination) {
        // 为一乘一面层保留分配空间。
        for (int level = 0; level < LAYERS - 1; level++) {
            long source = destination;
            destination += (FACE_SIZE * FACE_SIZE * 3 * 2 * 4) >> (level << 1);
            int width = (FACE_SIZE * 3) >> (level + 1);
            int sourceWidth = (FACE_SIZE * 3) >> level;
            int height = (FACE_SIZE * 2) >> (level + 1);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    long pixel = source + (x * 2 + y * 2 * sourceWidth) * 4;
                    int c00 = MemoryUtil.memGetInt(pixel);
                    int c01 = MemoryUtil.memGetInt(pixel + sourceWidth * 4);
                    int c10 = MemoryUtil.memGetInt(pixel + 4);
                    int c11 = MemoryUtil.memGetInt(pixel + sourceWidth * 4 + 4);
                    MemoryUtil.memPutInt(destination + (x + y * width) * 4L,
                            ColourMipFilter.blend(darkened, c00, c01, c10, c11));
                }
            }
        }
    }
}

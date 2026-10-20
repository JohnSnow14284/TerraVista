package dev.terravista.model;

import java.util.Arrays;

/** 计算捕获面的像素写入条件、透明覆盖、掩码与边界。 */
public final class FaceCoverage {
    public static final int STENCIL = 1;
    public static final int DEPTH = 2;
    public static final int ALPHA = 3;
    private FaceCoverage() {}

    static boolean written(ModelFacePixels face, int mode, int index) {
        if (mode == STENCIL) return (face.depthStencil()[index] & 0xFF) != 0;
        if (mode == DEPTH) return (face.depthStencil()[index] >>> 8) != (1 << 24) - 1;
        if (mode == ALPHA) return ((face.pixels()[index] >>> 24) & 0xFF) > 1;
        throw new IllegalArgumentException();
    }
    public static int writtenCount(ModelFacePixels face, int mode) {
        int count = 0;
        for (int i = 0; i < face.pixels().length; i++) count += written(face, mode, i) ? 1 : 0;
        return count;
    }
    public static boolean opaque(ModelFacePixels face) {
        for (int pixel : face.pixels()) if (((pixel >> 24) & 0xFF) != 255) return false;
        return true;
    }
    public static boolean translucentWhereWritten(ModelFacePixels face) {
        for (int i = 0; i < face.pixels().length; i++) {
            int alpha = face.pixels()[i] >>> 24;
            int depth = face.depthStencil()[i];
            if ((depth & 0xFF) != 0 && alpha != 0 && alpha != 255) return true;
        }
        return false;
    }
    public static boolean opaqueWhereWritten(ModelFacePixels face) {
        for (int i = 0; i < face.pixels().length; i++) {
            int alpha = face.pixels()[i] >>> 24;
            int depth = face.depthStencil()[i];
            if ((depth & 0xFF) != 0 && alpha != 255) return false;
        }
        return true;
    }
    public static long[] mask(ModelFacePixels face, int mode) {
        return mask(face, mode, new long[face.width() * face.height() / 64]);
    }
    public static long[] mask(ModelFacePixels face, int mode, long[] target) {
        Arrays.fill(target, 0);
        int index = 0;
        for (int y = 0; y < face.height(); y++) for (int x = 0; x < face.width(); x++) {
            if (written(face, mode, index)) target[index / 64] |= 1L << (index & 63);
            index++;
        }
        return target;
    }
    public static int[] bounds(ModelFacePixels face, int mode) {
        int minX = 0;
        minXCheck:
        do {
            for (int y = 0; y < face.height(); y++) {
                if (written(face, mode, minX + y * face.width())) break minXCheck;
            }
            minX++;
        } while (minX != face.width());
        int maxX = face.width() - 1;
        maxXCheck:
        do {
            for (int y = face.height() - 1; y != -1; y--) {
                if (written(face, mode, maxX + y * face.width())) break maxXCheck;
            }
            maxX--;
        } while (maxX != -1);
        // 沿 Y 扫描时以高度作为行步长。
        int minY = 0;
        minYCheck:
        do {
            for (int x = 0; x < face.width(); x++) {
                if (written(face, mode, minY * face.height() + x)) break minYCheck;
            }
            minY++;
        } while (minY != face.height());
        int maxY = face.height() - 1;
        maxYCheck:
        do {
            for (int x = face.width() - 1; x != -1; x--) {
                if (written(face, mode, maxY * face.height() + x)) break maxYCheck;
            }
            maxY--;
        } while (maxY != -1);
        return new int[]{minX, maxX, minY, maxY};
    }
}

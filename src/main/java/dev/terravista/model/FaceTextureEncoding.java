package dev.terravista.model;

/** 从打包的面通道中提取着色分类与归一化深度。 */
public final class FaceTextureEncoding {
    public static final int AVERAGE = 1;
    public static final int MAXIMUM = 2;
    public static final int MINIMUM = 3;
    private FaceTextureEncoding() {}

    public static int tint(ModelFacePixels face, int checkMode) {
        boolean all = true, some = false, any = false;
        var colors = face.pixels();
        var depths = face.depthStencil();
        for (int i = 0; i < colors.length; i++) {
            if (!FaceCoverage.written(face, checkMode, i)) continue;
            if ((colors[i] & 0xFFFFFF) == 0 || (colors[i] >>> 24) == 0) continue;
            boolean tinted = (depths[i] & (1 << 7)) != 0;
            any |= true;
            all &= tinted;
            some |= tinted;
        }
        if (!any) return 0;
        return some ? (all ? 3 : 2) : 1;
    }
    public static float depth(ModelFacePixels face, int mode, int checkMode) {
        var colors = face.pixels();
        var depths = face.depthStencil();
        long aggregate = 0, sum = 0;
        if (mode == MINIMUM) aggregate = Long.MAX_VALUE;
        if (mode == MAXIMUM) aggregate = Long.MIN_VALUE;
        for (int i = 0; i < colors.length; i++) {
            if (!FaceCoverage.written(face, checkMode, i)) continue;
            int depth = depths[i] >>> 8;
            if (mode == AVERAGE) { aggregate++; sum += depth; }
            else if (mode == MAXIMUM) aggregate = Math.max(aggregate, depth);
            else if (mode == MINIMUM) aggregate = Math.min(aggregate, depth);
        }
        if (mode == AVERAGE) return aggregate == 0 ? -1 : normalize((int) (sum / aggregate));
        if (mode == MAXIMUM) return aggregate == Long.MIN_VALUE ? -1 : normalize((int) aggregate);
        if (mode == MINIMUM) return aggregate == Long.MAX_VALUE ? -1 : normalize((int) aggregate);
        throw new IllegalArgumentException();
    }
    private static float normalize(int depth) { return (float) ((double) depth / ((1 << 24) - 1)); }
}

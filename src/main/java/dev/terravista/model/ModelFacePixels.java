package dev.terravista.model;

import java.util.Arrays;

/** 持有捕获的颜色与深度模板数组并缓存哈希。 */
public record ModelFacePixels(int[] pixels, int[] depthStencil, int width, int height, int signature) {
    public ModelFacePixels(int[] pixels, int[] depthStencil, int width, int height) {
        this(pixels, depthStencil, width, height,
                width * 312337173 * (Arrays.hashCode(pixels) ^ Arrays.hashCode(depthStencil)) ^ height);
    }
    @Override public boolean equals(Object object) {
        if (object == null) return false;
        var other = (ModelFacePixels) object;
        return signature == other.signature && Arrays.equals(other.pixels, pixels)
                && Arrays.equals(other.depthStencil, depthStencil);
    }
    @Override public int hashCode() { return signature; }
    public ModelFacePixels copy() {
        return new ModelFacePixels(Arrays.copyOf(pixels, pixels.length),
                Arrays.copyOf(depthStencil, depthStencil.length), width, height, signature);
    }
}

package dev.terravista.model;

import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import org.joml.Vector3f;
import java.util.Arrays;
import static dev.terravista.model.RasterPrecision.*;

/** 管理打包颜色、模板、深度目标与材质采样混合。 */
final class ModelFragments {
    private static final long DEPTH_MASK = ((1L << 24) - 1) << (64 - 24);
    final int size;
    final long[] pixels;
    boolean blending;
    boolean uvRaster;
    private int samplerWidth;
    private int samplerHeight;
    private int[] texture;

    ModelFragments(int size) {
        int expected = size * size;
        int actual = integer(fixed(size * size));
        if (expected != actual) throw new IllegalStateException(
                "TerraVista Models: Target resolution not supported, not enough precision bits. got: " + actual + ", expect: " + expected);
        this.size = size;
        pixels = new long[size * size];
    }
    void sampler(int[] texture, int width, int height) {
        if (texture.length != width * height) throw new IllegalArgumentException();
        this.texture = texture;
        samplerWidth = width;
        samplerHeight = height;
    }
    private int sample(float u, float v) {
        int x = Math.clamp(Math.round(u * samplerWidth - 0.5f), 0, samplerWidth - 1);
        int y = Math.clamp(Math.round(v * samplerHeight - 0.5f), 0, samplerHeight - 1);
        return texture[samplerWidth * y + x];
    }
    void clear() { Arrays.fill(pixels, DEPTH_MASK); }
    void shade(int index, float b1, float b2, float b3, float z, Vector3f a1, Vector3f a2, Vector3f a3) {
        z = Math.fma(z, 0.5f, 0.5f);
        if (z < 0.0f && -0.000001f <= z) z = 0;
        if (z < 0.0f || z > 1.0f) return;
        int metadata = Float.floatToRawIntBits(a1.x);
        float u = Math.fma(b1, a1.y, Math.fma(b2, a2.y, b3 * a3.y));
        float v = Math.fma(b1, a1.z, Math.fma(b2, a2.z, b3 * a3.z));
        int colour = sample(u, v);
        if ((metadata & 1) != 0 && (colour >>> 24) <= 0) return;
        if (uvRaster && !blending) {
            final int mask = (1 << 16) - 1;
            colour = ((int) (u * mask)) | (((int) (v * mask)) << 16);
        }
        pixels[index] += 1L << 32;
        long depth = ((long) (((double) z) * ((1 << 24) - 1))) << (64 - 24);
        if (depth == DEPTH_MASK) depth--;
        if (Long.compareUnsigned(pixels[index], depth) <= 0) return;
        pixels[index] &= ~DEPTH_MASK;
        pixels[index] |= depth;
        pixels[index] &= ~(1L << 39);
        pixels[index] |= ((long) (metadata & 4)) << 37;
        int previous = (int) pixels[index];
        pixels[index] &= ~Integer.toUnsignedLong(-1);
        if (blending) colour = blend(previous, colour);
        pixels[index] |= Integer.toUnsignedLong(colour);
    }
    private static int blend(int previous, int incoming) {
        int previousAlpha = (previous >>> 24) & 0xFF;
        if (previousAlpha == 0) return incoming;
        int incomingAlpha = (incoming >>> 24) & 0xFF;
        previous &= ~(0xFF << 24);
        incoming &= ~(0xFF << 24);
        int alpha = Math.min(0xFF, previousAlpha + ((incomingAlpha * (255 - previousAlpha)) >> 8));
        return ColorMixer.mix(incoming, previous, incomingAlpha) | (alpha << 24);
    }
}

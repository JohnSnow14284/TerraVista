package dev.terravista.model;

import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.util.ARGB;

/** 按固定累加顺序进行四采样 sRGB mip 过滤与透明度转换。 */
public final class ColourMipFilter {
    private ColourMipFilter() {}
    public static int blend(boolean darkened, int c00, int c01, int c10, int c11) {
        boolean includeTransparent = !darkened;
        float r = 0.0f, g = 0.0f, b = 0.0f, a = 0.0f;
        if (includeTransparent || (c00 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((c00 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((c00 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((c00 >> 16) & 0xFF);
            a += includeTransparent ? (c00 >>> 24) : ColorSRGB.srgbToLinear(c00 >>> 24);
        }
        if (includeTransparent || (c01 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((c01 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((c01 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((c01 >> 16) & 0xFF);
            a += includeTransparent ? (c01 >>> 24) : ColorSRGB.srgbToLinear(c01 >>> 24);
        }
        if (includeTransparent || (c10 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((c10 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((c10 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((c10 >> 16) & 0xFF);
            a += includeTransparent ? (c10 >>> 24) : ColorSRGB.srgbToLinear(c10 >>> 24);
        }
        if (includeTransparent || (c11 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((c11 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((c11 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((c11 >> 16) & 0xFF);
            a += includeTransparent ? (c11 >>> 24) : ColorSRGB.srgbToLinear(c11 >>> 24);
        }
        return ColorSRGB.linearToSrgb(r / 4, g / 4, b / 4,
                includeTransparent ? ((int) a) / 4 : ARGB.linearToSrgbChannel(a / 4));
    }
}

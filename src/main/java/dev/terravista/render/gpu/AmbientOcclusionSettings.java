package dev.terravista.render.gpu;

import dev.terravista.config.ClientOptions.SSAOMode;
import dev.terravista.render.DepthConvention;
import net.minecraft.client.Minecraft;

/** 选择环境遮蔽采样档位并生成着色器采样盘。 */
final class AmbientOcclusionSettings {
    final boolean advanced;
    final int samples;

    AmbientOcclusionSettings(SSAOMode requested) {
        var selected = select(requested);
        advanced = selected != SSAOMode.BASIC;
        samples = switch (selected) {
            case BETTER -> 12;
            case BEST -> 24;
            default -> 0;
        };
    }

    private static SSAOMode select(SSAOMode mode) {
        if (mode != SSAOMode.AUTO) return mode;
        long width = Minecraft.getInstance().getWindow().getWidth();
        long height = Minecraft.getInstance().getWindow().getHeight();
        long pixels = Math.max(1, width * height);
        if (pixels * 24 < 50_000_000L) return SSAOMode.BEST;
        if (pixels * 12 < 150_000_000L) return SSAOMode.BETTER;
        return SSAOMode.BASIC;
    }

    String shader(DepthConvention convention) {
        var definitions = ShaderVariants.defines().depth(convention);
        if (advanced) definitions.define("BETTER_SSAO").define("SSAO_STEPS", samples).define("USE_GENERATED_SAMPLE_POINTS");
        String source = ShaderVariants.load(ShaderProgram.OCCLUSION_APPLY, definitions.build());
        return advanced ? source.replace("%%CONST_ARRAY%%", sampleDisk(samples)) : source;
    }

    static String sampleDisk(int samples) {
        var source = new StringBuilder();
        for (int i = 0; i < samples; i++) {
            source.append("vec2(");
            float angleFraction = (((float) i) + 0.5f) * (1.0f / samples);
            float base = (float) (i * (1.0 / 1.6180339887) + 0.5);
            float radius = (float) Math.sqrt(base % 1);
            float angle = angleFraction * 6.2831853f;
            source.append((float) (radius * Math.cos(angle))).append("f, ")
                    .append((float) (radius * Math.sin(angle))).append("f)");
            if (i != samples - 1) source.append(", ");
        }
        return source.toString();
    }
}

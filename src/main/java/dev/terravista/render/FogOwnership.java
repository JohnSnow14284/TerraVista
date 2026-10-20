package dev.terravista.render;

/** 分配地形距离雾与环境、天空和云层雾的范围。 */
public final class FogOwnership {
    public static final float UNBOUNDED = 999999999.0F;

    public record Ranges(float environmentStart, float environmentEnd,
                         float distanceStart, float distanceEnd, float skyEnd, float cloudEnd) {}

    public static Ranges apply(Ranges source, boolean active, boolean removeEnvironment) {
        if (!active) return source;
        boolean clearEnvironment = removeEnvironment && source.environmentEnd >= 10.0F;
        return new Ranges(clearEnvironment ? UNBOUNDED : source.environmentStart,
                clearEnvironment ? UNBOUNDED : source.environmentEnd,
                UNBOUNDED, UNBOUNDED, source.skyEnd, source.cloudEnd);
    }

    /** 按距离和浓度设置自定义雾，保留浓环境雾及天空、云层范围。 */
    public static Ranges custom(Ranges source, int strength, int chunks) {
        if (source.environmentEnd < 10.0F) return source;
        float density = Math.clamp(strength, 0, 100) / 100f;
        float distance = Math.clamp(chunks, 8, 8192) * 16f;
        float start = density == 0 ? UNBOUNDED : distance * 0.35f;
        float end = density == 0 ? UNBOUNDED : start + (distance - start) / density;
        return new Ranges(start, end, source.distanceStart, source.distanceEnd, source.skyEnd, source.cloudEnd);
    }

    private FogOwnership() {}
}

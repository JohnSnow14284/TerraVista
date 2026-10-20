package dev.terravista.model;

import org.joml.Vector3f;
import org.joml.Vector3i;

/** 执行有符号定点缩放与截断运算。 */
final class RasterPrecision {
    private static final int FRACTION_BITS = 32 - (13 + 1);
    private static final long SCALE = (1 << FRACTION_BITS) - 1;
    private RasterPrecision() {}
    static int fixed(float value) { return (int) (((double) value) * (double) SCALE); }
    static int fixed(int value) { return (int) (value * SCALE); }
    static void fixed(Vector3i target, Vector3f value) {
        target.set(fixed(value.x), fixed(value.y), fixed(value.z));
    }
    static float real(int value) { return (float) (((double) value) / (double) SCALE); }
    static int integer(int value) { return (int) (value / SCALE); }
    static int multiply(int a, int b) { return (int) ((((long) a) * ((long) b)) / SCALE); }
    static int divide(int a, int b) { return (int) ((((long) a) * SCALE) / b); }
    static int edge(Vector3i a, Vector3i b, Vector3i c) { return edge(a, b, c.x, c.y); }
    static int edge(Vector3i a, Vector3i b, int x, int y) {
        return multiply(x - a.x, b.y - a.y) - multiply(y - a.y, b.x - a.x);
    }
}

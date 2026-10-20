package dev.terravista.model;

import net.minecraft.client.resources.model.geometry.BakedQuad;

/** 识别穿过单元中心的竖直双向交叉植物面。 */
final class CrossPlantGeometry {
    private CrossPlantGeometry() {}
    static int family(BakedQuad quad) {
        var a = quad.position(0); var b = quad.position(1); var c = quad.position(2); var d = quad.position(3);
        float ax = b.x() - a.x(), ay = b.y() - a.y(), az = b.z() - a.z();
        float bx = c.x() - a.x(), by = c.y() - a.y(), bz = c.z() - a.z();
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 0.0001f || Math.abs(ny) > length * 0.12f) return 0;
        float major = Math.max(Math.abs(nx), Math.abs(nz)), minor = Math.min(Math.abs(nx), Math.abs(nz));
        if (major < 0.00001f || minor < major * 0.55f) return 0;
        float x = (a.x() + b.x() + c.x() + d.x()) * 0.25f - 0.5f;
        float y = (a.y() + b.y() + c.y() + d.y()) * 0.25f - 0.5f;
        float z = (a.z() + b.z() + c.z() + d.z()) * 0.25f - 0.5f;
        if (Math.abs(nx * x + ny * y + nz * z) > length * 0.0625f) return 0;
        return nx * nz >= 0 ? 1 : 2;
    }
    static void center(float[] depths) {
        for (int face = 2; face < 6; face++) if (depths[face] > -0.1f) depths[face] = 0.5f;
    }
}

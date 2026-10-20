package dev.terravista.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import static dev.terravista.model.RasterPrecision.*;

/** 投影四边形并执行有符号定点三角形覆盖测试。 */
public final class TriangleRasterizer {
    private final Vector4f projected = new Vector4f();
    private final Vector3f[] positions = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f[] attributes = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3i[] corners = {new Vector3i(), new Vector3i(), new Vector3i()};
    private final Vector3f[] triangleAttributes = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final ModelFragments target;
    private boolean cullBackFaces;

    public TriangleRasterizer(int size) { target = new ModelFragments(size); }
    public void cullBackFaces(boolean enabled) { cullBackFaces = enabled; }
    public void blending(boolean enabled) { target.blending = enabled; }
    public void uvRaster(boolean enabled) { target.uvRaster = enabled; }
    public void sampler(int[] texture, int width, int height) { target.sampler(texture, width, height); }
    public void clear() { target.clear(); }
    public long[] pixels() { return target.pixels; }
    public void draw(Matrix4f transform, RasterVertices vertices) {
        draw(transform, vertices.address(), vertices.quadCount());
    }
    public void draw(Matrix4f transform, long vertices, int quadCount) {
        if (quadCount == 0) return;
        if (target.blending && target.uvRaster) throw new IllegalStateException("TerraVista Models: Blending and UV raster both enabled");
        for (int quad = 0; quad < quadCount; quad++) {
            long address = vertices + RasterVertices.STRIDE * 4L * quad;
            for (int vertex = 0; vertex < 4; vertex++) project(transform, address, vertex);
            triangle(0, 1, 2, false);
            triangle(2, 3, 0, true);
        }
    }
    private void project(Matrix4f transform, long address, int vertex) {
        projected.setFromAddress(address + vertex * RasterVertices.STRIDE);
        attributes[vertex].setFromAddress(address + vertex * RasterVertices.STRIDE + 3 * 4);
        projected.w = 1.0f;
        var vector = transform.transformProject(projected);
        if (Math.abs(projected.w - 1.0f) > 0.000001f) throw new IllegalStateException();
        positions[vertex].set(Math.fma(vector.x, 0.5f, 0.5f) * target.size,
                Math.fma(vector.y, 0.5f, 0.5f) * target.size, vector.z);
    }
    private void triangle(int first, int second, int third, boolean includeEdges) {
        fixed(corners[0], positions[first]);
        fixed(corners[1], positions[second]);
        fixed(corners[2], positions[third]);
        triangleAttributes[0].set(attributes[first]);
        triangleAttributes[1].set(attributes[second]);
        triangleAttributes[2].set(attributes[third]);
        var a = corners[0]; var b = corners[1]; var c = corners[2];
        int area = edge(a, b, c);
        if (area < 0 == cullBackFaces) return;
        if (Math.abs(real(area)) < 0.001) return;
        int minX = integer(Math.max(Math.min(Math.min(a.x, b.x), c.x), 0));
        int maxX = integer(Math.min(Math.max(Math.max(a.x, b.x), c.x), fixed(target.size - 1)));
        int minY = integer(Math.max(Math.min(Math.min(a.y, b.y), c.y), 0));
        int maxY = integer(Math.min(Math.max(Math.max(a.y, b.y), c.y), fixed(target.size - 1)));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int cx = fixed(x) + fixed(0.5f);
                int cy = fixed(y) + fixed(0.5f);
                int w1 = divide(edge(b, c, cx, cy), area);
                int w2 = divide(edge(c, a, cx, cy), area);
                int w3 = fixed(1.0f) - w1 - w2;
                if ((w1 > 0 && w2 > 0 && w3 > 0) || (includeEdges && w1 >= 0 && w2 >= 0 && w3 >= 0)) {
                    float b1 = real(w1), b2 = real(w2), b3 = real(w3);
                    float z = Math.fma(b1, real(corners[0].z), Math.fma(b2, real(corners[1].z), b3 * real(corners[2].z)));
                    target.shade(x + y * target.size, b1, b2, b3, z,
                            triangleAttributes[0], triangleAttributes[1], triangleAttributes[2]);
                }
            }
        }
    }
}

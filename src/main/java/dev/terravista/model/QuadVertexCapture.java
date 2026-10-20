package dev.terravista.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/** 适配 Minecraft 顶点与材质，顶点流负责原生内存。 */
public final class QuadVertexCapture implements VertexConsumer {
    private final RasterVertices vertices;
    public QuadVertexCapture(RasterVertices vertices) { this.vertices = vertices; }
    public RasterVertices vertices() { return vertices; }

    @Override public QuadVertexCapture addVertex(float x, float y, float z) {
        vertices.position(x, y, z); return this;
    }
    @Override public QuadVertexCapture setUv(float u, float v) { vertices.uv(u, v); return this; }

    // 仅采集二十四字节顶点格式中的属性。
    @Override public QuadVertexCapture setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int colour) { return this; }
    @Override public QuadVertexCapture setUv1(int u, int v) { return this; }
    @Override public QuadVertexCapture setUv2(int u, int v) { return this; }
    @Override public QuadVertexCapture setUv3(float u, float v) { return this; }
    @Override public QuadVertexCapture setNormal(float x, float y, float z) { return this; }
    @Override public QuadVertexCapture setLineWidth(float width) { return this; }

    public QuadVertexCapture quad(BakedQuad quad) { return quad(quad, false); }
    public QuadVertexCapture quad(BakedQuad quad, boolean forceSolid) {
        int metadata = 0;
        metadata |= forceSolid ? 0 : (quad.materialInfo().layer() != ChunkSectionLayer.SOLID ? 1 : 0);
        metadata |= quad.materialInfo().isTinted() ? 4 : 0;
        return quad(quad, metadata);
    }
    public QuadVertexCapture quad(BakedQuad quad, int metadata) {
        vertices.markShaded(quad.materialInfo().shadeDirectionOverride() == null);
        vertices.markDarkened(quad.materialInfo().sprite().contents().mipmapStrategy == MipmapStrategy.DARK_CUTOUT);
        vertices.reserveQuad();
        for (int i = 0; i < 4; i++) {
            var position = quad.position(i);
            addVertex(position.x(), position.y(), position.z());
            long uv = quad.packedUV(i);
            setUv(UVPair.unpackU(uv), UVPair.unpackV(uv));
            vertices.metadata(metadata | vertices.globalMetadata());
        }
        return this;
    }
}

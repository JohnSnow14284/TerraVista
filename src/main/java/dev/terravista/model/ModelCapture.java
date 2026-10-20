package dev.terravista.model;

import com.mojang.renderpearl.api.GpuFormat;
import dev.terravista.memory.NativeMemory;
import dev.terravista.render.AtlasReadback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

/** 收集方块与流体几何并捕获六个打包的 CPU 面。 */
public final class ModelCapture {
    private final RasterVertices opaque = new RasterVertices();
    private final RasterVertices translucent = new RasterVertices(1);
    private final TriangleRasterizer rasterizer = new TriangleRasterizer(TextureMipmaps.FACE_SIZE);
    private final QuadVertexCapture opaqueCapture = new QuadVertexCapture(opaque);
    private final QuadVertexCapture translucentCapture = new QuadVertexCapture(translucent);
    private final FluidGeometryCapture fluids;
    private static final long FACE_BYTES = TextureMipmaps.FACE_SIZE * TextureMipmaps.FACE_SIZE * 8;

    public ModelCapture() { fluids = new FluidGeometryCapture(opaqueCapture, translucentCapture); }
    public void setupTexture() {
        var texture = Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")).getTexture();
        if (texture.getFormat() != GpuFormat.RGBA8_UNORM)
            throw new IllegalStateException("TerraVista Models: Block atlas not rgba8: " + texture.getFormat());
        int mip = 0;
        int width = texture.getWidth(mip);
        int height = texture.getHeight(mip);
        int[] pixels = AtlasReadback.current().read(texture, width, height);
        rasterizer.sampler(pixels, width, height);
    }
    public void free() { opaque.free(); translucent.free(); }
    public int render(BlockState state, long output) { return render(state, output, false); }
    public int render(BlockState state, long output, boolean uv) {
        MemoryUtil.memSet(output, 0, 16 * 16 * 8 * 6);
        boolean block = !(state.getBlock() instanceof LiquidBlock);
        // 查询方块实体属性后继续采集方块模型。
        state.hasBlockEntity();
        int flags = 0;
        if (block) {
            opaque.reset(); translucent.reset();
            if (BlockGeometryCapture.collect(state, opaqueCapture, translucentCapture)) flags |= 16;
            flags |= flags();
            if (!(opaque.empty() && translucent.empty())) {
                for (int face = 0; face < ModelViews.COUNT; face++) renderFace(face, output, uv);
            }
        } else {
            if (!(state.getBlock() instanceof LiquidBlock)) throw new IllegalStateException();
            for (int face = 0; face < ModelViews.COUNT; face++) {
                opaque.reset(); translucent.reset();
                fluids.collect(state, face);
                if (opaque.empty() && translucent.empty()) continue;
                flags |= flags();
                renderFace(face, output, uv);
            }
        }
        return flags;
    }
    private int flags() {
        return ((opaque.shaded() | translucent.shaded()) ? 1 : 0)
                | ((opaque.darkened() | translucent.darkened()) ? 2 : 0)
                | (!translucent.empty() ? 4 : 0) | (opaque.discard() ? 8 : 0);
    }
    private void renderFace(int face, long output, boolean uv) {
        rasterizer.cullBackFaces(face == 1 || face == 2 || face == 4);
        rasterizer.clear();
        rasterizer.uvRaster(uv);
        rasterizer.blending(false);
        rasterizer.draw(ModelViews.view(face), opaque);
        rasterizer.blending(!uv);
        rasterizer.draw(ModelViews.view(face), translucent);
        NativeMemory.copy(rasterizer.pixels(), output + FACE_BYTES * face);
    }
}

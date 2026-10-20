package dev.terravista.model;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LiquidBlock;
import org.lwjgl.system.MemoryUtil;
import static dev.terravista.model.TextureMipmaps.FACE_SIZE;

/** 按阈值与位布局打包模型面和网格元数据。 */
final class ModelPacking {
    private ModelPacking() {}
    static float[] depths(ModelFacePixels[] textures, int checkMode) {
        return depths(textures, checkMode, FaceTextureEncoding.AVERAGE);
    }
    static float[] depths(ModelFacePixels[] textures, int checkMode, int mode) {
        float[] result = new float[6];
        for (var direction : Direction.values()) {
            var face = textures[direction.get3DDataValue()];
            float depth = FaceTextureEncoding.depth(face, mode, checkMode);
            result[direction.ordinal()] = depth < -0.1 ? -1 : depth;
        }
        return result;
    }
    static boolean doubleSided(float[] depths) {
        return (depths[0] < -0.1 && depths[1] < -0.1)
                || (depths[2] < -0.1 && depths[3] < -0.1)
                || (depths[4] < -0.1 && depths[5] < -0.1);
    }
    static long header(boolean biome, ChunkSectionLayer layer, boolean doubleSided,
                       boolean containsFluid, boolean fluid, boolean selfCulls) {
        long metadata = 0;
        metadata |= biome ? 1 : 0;
        metadata |= layer == ChunkSectionLayer.TRANSLUCENT ? 2 : 0;
        metadata |= doubleSided ? 4 : 0;
        metadata |= containsFluid ? 8 : 0;
        metadata |= fluid ? 16 : 0;
        metadata |= selfCulls ? 32 : 0;
        return metadata;
    }
    static long faces(long destination, ModelFacePixels[] textures, float[] depths, int checkMode,
                      ChunkSectionLayer layer, long metadata, boolean tinted, BlockState state) {
        boolean fullyOpaque = true;
        for (int face = 5; face != -1; face--) {
            long output = destination + 4L * face;
            metadata <<= 8;
            float offset = depths[face];
            if (offset < -0.1) {
                metadata |= 0xFF;
                MemoryUtil.memPutInt(output, -1);
                fullyOpaque = false;
                continue;
            }
            var bounds = FaceCoverage.bounds(textures[face], checkMode);
            int written = FaceCoverage.writtenCount(textures[face], checkMode);
            boolean fullBlock = bounds[0] == 0 && bounds[2] == 0
                    && bounds[1] == FACE_SIZE - 1 && bounds[3] == FACE_SIZE - 1;
            metadata |= fullBlock ? 2 : 0;
            boolean boundary = offset < 1.0f / 128.0f;
            boolean occludes = layer != ChunkSectionLayer.TRANSLUCENT
                    && !(state.getBlock() instanceof LiquidBlock) && fullBlock
                    && written == FACE_SIZE * FACE_SIZE && boundary;
            if (occludes) occludes = FaceTextureEncoding.depth(textures[face],
                    FaceTextureEncoding.MAXIMUM, checkMode) < 1.0f / 128.0f;
            metadata |= occludes ? 1 : 0;
            fullyOpaque &= occludes;
            boolean canBeOccluded = boundary;
            metadata |= canBeOccluded ? 4 : 0;
            boolean selfLighting = offset > 0.01 || layer == ChunkSectionLayer.TRANSLUCENT
                    && !(state.getBlock() instanceof net.minecraft.world.level.block.IceBlock);
            metadata |= selfLighting ? 8 : 0;
            if (FACE_SIZE - 1 != 15) {
                for (int i = 0; i < 4; i++) bounds[i] = Math.round((((float) bounds[i]) / (FACE_SIZE - 1)) * 15);
            }
            int word = bounds[0] | (bounds[1] << 4) | (bounds[2] << 8) | (bounds[3] << 12);
            int encodedDepth = Math.round(offset * 64);
            word |= Math.min(encodedDepth, 62) << 16;
            int area = (bounds[1] - bounds[0] + 1) * (bounds[3] - bounds[2] + 1);
            boolean discard = ((float) written) / area < 0.9;
            discard |= layer != ChunkSectionLayer.SOLID;
            discard &= layer != ChunkSectionLayer.TRANSLUCENT;
            word |= discard ? 1 << 22 : 0;
            word |= ((!fullBlock) && layer != ChunkSectionLayer.TRANSLUCENT) ? 1 << 23 : 0;
            if (tinted) {
                int tint = FaceTextureEncoding.tint(textures[face], checkMode);
                if (tint == 2) word |= 1 << 24;
                else if (tint == 3) word |= 2 << 24;
            }
            MemoryUtil.memPutInt(output, word);
        }
        metadata |= fullyOpaque ? 1L << (48 + 6) : 0;
        metadata |= ((long) light(state)) << (48 + 7);
        return metadata;
    }
    static int light(BlockState state) {
        boolean emissive = state.emissiveRendering();
        if (emissive) return 15;
        return Math.clamp(state.getLightEmission(), 0, 15);
    }
    static int flags(boolean tinted, boolean biome, ChunkSectionLayer layer, boolean shaded,
                     boolean fluid, boolean doubleSided) {
        int flags = 0;
        flags |= tinted ? 1 : 0;
        flags |= biome ? 2 : 0;
        flags |= layer == ChunkSectionLayer.TRANSLUCENT ? 4 : 0;
        flags |= shaded ? 8 : 0;
        flags |= fluid ? 16 : 0;
        flags |= doubleSided ? 32 : 0;
        return flags;
    }
}

package dev.terravista.mesh;

import dev.terravista.model.ModelMetadata;

/** 生成四边形属性并判断面与方块光照。 */
final class FaceEmission {
    static final long LIGHT = 0xFFL << 55;
    private FaceEmission() {}
    static long typing(long metadata) {
        return 0b111L & (0b000_000_010_100L >> (ModelMetadata.translucentBit(metadata) * 6 + ModelMetadata.doubleSidedBit(metadata) * 3));
    }
    static long attributes(int modelId, long state, long metadata) {
        long lightAndBiome = (state & ((0x1FFL << 47) | (0xFFL << 56))) >>> 1;
        lightAndBiome &= ~(ModelMetadata.notBiomeColouredBit(metadata) * (0x1FFL << 46));
        lightAndBiome &= ~(ModelMetadata.fullyOpaqueBit(metadata) * (0xFFL << 55));
        long data = lightAndBiome;
        data |= Integer.toUnsignedLong(modelId) << 26;
        data |= typing(metadata);
        int height = dev.terravista.world.VoxelBits.surfaceHeight(state);
        if (height != 0 && (height != 256 || !ModelMetadata.fullyOpaque(metadata))
                && !ModelMetadata.doubleSided(metadata) && !ModelMetadata.fluid(metadata)
                && !ModelMetadata.containsFluid(metadata)) data |= (long) height << 3;
        return data;
    }
    static boolean visible(int face, long quad, long metadata, long neighbor, long neighborMetadata) {
        if (((quad ^ neighbor) & (0xFFFFL << 26)) == 0 && ModelMetadata.cullsSame(metadata)) return false;
        if (!ModelMetadata.faceExists(metadata, face)) return false;
        if (ModelMetadata.faceCanBeOccluded(metadata, face))
            if (ModelMetadata.faceOccludes(neighborMetadata, face ^ 1)) return false;
        return true;
    }
    static void nonOpaque(int face, long quad, long metadata, long neighbor, long neighborMetadata,
                          PackedQuadWriter.Plane plane) {
        if (visible(face, quad, metadata, neighbor, neighborMetadata)) {
            plane.accept(light((long) (face & 1) | (quad & ~LIGHT)
                    | ((ModelMetadata.faceUsesSelfLighting(metadata, face) ? quad : neighbor) & LIGHT), metadata));
        } else plane.skip(1);
    }
    static long light(long quad, long metadata) {
        final long blockLightMask = 0xFL << (55 + 4);
        long blockLight = quad & blockLightMask;
        blockLight = Math.max(blockLight, ModelMetadata.lightEmission(metadata) << (55 + 4));
        quad &= ~blockLightMask;
        quad |= blockLight;
        return quad;
    }
}

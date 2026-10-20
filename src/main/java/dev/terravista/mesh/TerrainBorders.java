package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.world.VoxelBits;
import dev.terravista.world.ResidentSection;

/** 补齐表面高度差及区段边缘被同级邻居遮去的立面。 */
final class TerrainBorders {
    private final SectionSamples samples;
    private final ModelCatalog models;
    private final FluidSurface neighborhood;
    private final PackedQuadWriter.Plane plane;

    TerrainBorders(SectionSamples samples, ModelCatalog models, FluidSurface neighborhood, PackedQuadWriter writer) {
        this.samples = samples; this.models = models; this.neighborhood = neighborhood; plane = writer.plane();
    }

    void emit(ResidentSection section) {
        long[] raw = section.contents().blocks();
        plane.offset = false;
        for (int row = 0; row < 1024; row++) {
            int candidates = samples.opaque[row] | samples.nonOpaque[row];
            int exposed = row < 992 ? candidates & ~samples.opaque[row + 32] : candidates;
            int lowered = samples.recessed[row];
            int beside = (lowered << 1) | (lowered >>> 1);
            if ((row & 31) != 0) beside |= samples.recessed[row - 1];
            if ((row & 31) != 31) beside |= samples.recessed[row + 1];
            int borderCells = (row & 31) == 0 || (row & 31) == 31 ? -1 : 0x80000001;
            candidates &= exposed | beside | borderCells;
            while (candidates != 0) {
                int cell = row * 32 + Integer.numberOfTrailingZeros(candidates);
                candidates &= candidates - 1;
                long data = samples.cells[cell * 2], metadata = samples.cells[cell * 2 + 1];
                if (metadata == 0 || ModelMetadata.fluid(metadata) || ModelMetadata.containsFluid(metadata)
                        || ModelMetadata.translucent(metadata) || ModelMetadata.doubleSided(metadata)) continue;
                int x = cell & 31, z = (cell >>> 5) & 31, y = cell >>> 10;
                long above = y == 31 ? samples.neighbors[3 * 1024 + z * 32 + x] : raw[cell + 1024];
                boolean covered = !VoxelBits.isAir(above)
                        && ModelMetadata.faceOccludes(models.metadata(models.modelId(VoxelBits.block(above))), 0);
                int height = (int) (data >>> 3) & 511;
                if (height == 0) height = 256;
                for (int face = 2; face < 6; face++) {
                    int axis = face >> 1, side = face & 1, coordinate = axis == 1 ? z : x;
                    boolean border = coordinate == (side == 0 ? 0 : 31);
                    long neighbor;
                    if (border) {
                        int slice = axis == 1 ? 4 + side : side;
                        neighbor = samples.neighbors[slice * 1024 + (axis == 1 ? y * 32 + x : y * 32 + z)];
                    } else neighbor = raw[cell + (side * 2 - 1) * (axis == 1 ? 32 : 1)];
                    if (VoxelBits.isAir(neighbor)) continue;
                    int otherCell = cell + (side * 2 - 1) * (axis == 1 ? 32 : 1);
                    int model = border ? models.modelId(VoxelBits.block(neighbor))
                            : (int) (samples.cells[otherCell * 2] >>> 26) & 65535;
                    long other = border ? models.metadata(model) : samples.cells[otherCell * 2 + 1];
                    if (!ModelMetadata.faceOccludes(other, face ^ 1)
                            && !(((data >>> 26) & 65535) == model && ModelMetadata.cullsSame(other))) continue;
                    int neighborHeight = border ? VoxelBits.surfaceHeight(neighbor)
                            : (int) (samples.cells[otherCell * 2] >>> 3) & 511;
                    long outerAbove = 0;
                    boolean sampledAbove = border && neighborHeight > 0 && neighborHeight < 256;
                    if (sampledAbove) {
                        outerAbove = neighborhood.voxel(x + (axis == 2 ? side * 2 - 1 : 0), y + 1,
                                z + (axis == 1 ? side * 2 - 1 : 0));
                        if (!VoxelBits.isAir(outerAbove) && ModelMetadata.faceOccludes(
                                models.metadata(models.modelId(VoxelBits.block(outerAbove))), 0)) neighborHeight = 256;
                    }
                    if ((!border || covered) && height <= (neighborHeight == 0 ? 256 : neighborHeight)) continue;
                    if (!ModelMetadata.faceExists(metadata, face)) continue;
                    plane.axis = axis; plane.position = coordinate;
                    if (!sampledAbove) outerAbove = !border && y < 31 ? raw[otherCell + 1024]
                            : neighborhood.voxel(x + (axis == 2 ? side * 2 - 1 : 0), y + 1,
                                    z + (axis == 1 ? side * 2 - 1 : 0));
                    int a = VoxelBits.light(above), b = VoxelBits.light(outerAbove);
                    int light = Math.max(a & 15, b & 15) | Math.max(a & 240, b & 240);
                    long quad = (data & ~FaceEmission.LIGHT) | ((long) light << 55) | side;
                    plane.emit(axis == 1 ? x : z, y, 1, 1, FaceEmission.light(quad, metadata));
                }
            }
        }
    }
}

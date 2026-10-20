package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.world.VoxelBits;
import static dev.terravista.mesh.FaceEmission.LIGHT;
import static dev.terravista.mesh.FaceEmission.light;

/** 按顺序遍历不透明面并写入共享平面流。 */
final class OpaqueFaces {
    private static final long LANES = 0x4210842108421L;
    private final SectionSamples samples;
    private final ModelCatalog models;
    private final PackedQuadWriter.Plane plane;
    private final PackedQuadWriter.Plane[] xPlanes;

    OpaqueFaces(SectionSamples samples, ModelCatalog models, PackedQuadWriter.Plane plane,
                PackedQuadWriter.Plane[] xPlanes) {
        this.samples = samples; this.models = models; this.plane = plane; this.xPlanes = xPlanes;
    }
    void yzInner(int axis) {
        for (int layer = 0; layer < 31; layer++) {
            plane.position = layer;
            int skipped = 0;
            for (int row = 0; row < 32; row++) {
                int index = axis == 0 ? layer * 32 + row : row * 32 + layer;
                int stride = axis == 0 ? 32 : 1;
                int current = samples.opaque[index], next = samples.opaque[index + stride];
                int faces = current ^ next;
                if (faces == 0) { skipped += 32; continue; }
                plane.skip(skipped); skipped = 0;
                int forward = faces & current, previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) plane.skip(gap);
                    faces &= ~Integer.lowestOneBit(faces);
                    int side = (forward >> x) & 1;
                    int cell = (x + index * 32) * 2, shift = stride * 64;
                    int self = cell + (side == 1 ? 0 : shift), other = cell + (side == 1 ? shift : 0);
                    long quad = samples.cells[self], metadata = samples.cells[self + 1], neighbor = samples.cells[other];
                    long neighborMetadata = samples.cells[other + 1];
                    boolean culled = ((quad ^ neighbor) & (0xFFFFL << 26)) == 0 && ModelMetadata.cullsSame(neighborMetadata);
                    culled |= ModelMetadata.faceOccludes(neighborMetadata, (axis << 1) | (1 - side));
                    if (culled) { plane.skip(1); continue; }
                    plane.accept(light((long) side | (quad & ~LIGHT) | (neighbor & LIGHT), metadata));
                }
                plane.endRow();
            }
            plane.finish();
        }
    }
    void yzOuter(int axis) {
        plane.offset = false;
        for (int side = 0; side < 2; side++) {
            int layer = side == 0 ? 0 : 31;
            plane.position = layer;
            int skipped = 0;
            for (int row = 0; row < 32; row++) {
                int index = axis == 0 ? layer * 32 + row : row * 32 + layer;
                int faces = samples.opaque[index];
                if (faces == 0) { skipped += 32; continue; }
                plane.skip(skipped); skipped = 0;
                int previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) plane.skip(gap);
                    faces &= ~Integer.lowestOneBit(faces);
                    int cell = (x + index * 32) * 2;
                    long neighbor = samples.neighbors[(axis + 1) * 2048 + side * 1024 + row * 32 + x];
                    long quad = samples.cells[cell], metadata = samples.cells[cell + 1];
                    int block = VoxelBits.block(neighbor);
                    if (block != 0) {
                        int model = models.modelId(block);
                        long neighborMetadata = models.metadata(model);
                        if (ModelMetadata.fullyOpaque(neighborMetadata)) { plane.skip(1); continue; }
                        boolean culled = model == ((quad >> 26) & 0xFFFF) && ModelMetadata.cullsSame(neighborMetadata);
                        culled |= ModelMetadata.faceOccludes(neighborMetadata, (axis << 1) | (1 - side));
                        if (culled) { plane.skip(1); continue; }
                    }
                    plane.accept(light((long) side | (quad & ~LIGHT) | ((neighbor & (0xFFL << 56)) >>> 1), metadata));
                }
                plane.endRow();
            }
            plane.finish();
        }
        plane.offset = true;
    }
    void xInner() {
        for (int y = 0; y < 32; y++) {
            long low = 0, middle = 0, high = 0;
            int neverUsed = -1, faces = 0;
            for (int z = 0; z < 32; z++) {
                int occupied = samples.opaque[y * 32 + z];
                faces = (occupied ^ (occupied >>> 1)) & (-1 >>> 1);
                low += LANES; middle += LANES; high += LANES;
                neverUsed &= ~faces;
                if (z == 30 && neverUsed != 0) {
                    int pending = neverUsed;
                    while (pending != 0) {
                        int index = Integer.numberOfTrailingZeros(pending);
                        pending &= ~Integer.lowestOneBit(pending);
                        xPlanes[index].skip(31);
                    }
                    low &= ~(Long.expand(Integer.toUnsignedLong(neverUsed), LANES) * 0x1F);
                    middle &= ~(Long.expand(Integer.toUnsignedLong(neverUsed) >> 11, LANES) * 0x1F);
                    high &= ~(Long.expand(Integer.toUnsignedLong(neverUsed) >> 22, LANES) * 0x1F);
                }
                if (faces == 0) continue;
                int forward = faces & occupied, pending = faces;
                while (pending != 0) {
                    int index = Integer.numberOfTrailingZeros(pending);
                    pending &= ~Integer.lowestOneBit(pending);
                    var stream = xPlanes[index];
                    int skipped;
                    if (index < 11) {
                        skipped = (int) (low >> (index * 5));
                        low &= ~(0x1FL << (index * 5));
                    } else if (index < 22) {
                        skipped = (int) (middle >> ((index - 11) * 5));
                        middle &= ~(0x1FL << ((index - 11) * 5));
                    } else {
                        skipped = (int) (high >> ((index - 22) * 5));
                        high &= ~(0x1FL << ((index - 22) * 5));
                    }
                    skipped = (skipped & 31) - 1;
                    if (skipped != 0) stream.skip(skipped);
                    int side = (forward >> index) & 1;
                    int cell = (index + z * 32 + y * 1024) * 2;
                    int self = cell + (side == 1 ? 0 : 2), other = cell + (side == 1 ? 2 : 0);
                    if (ModelMetadata.faceOccludes(samples.cells[other + 1], (2 << 1) | (1 - side))) {
                        stream.skip(1); continue;
                    }
                    long quad = samples.cells[self], metadata = samples.cells[self + 1], neighbor = samples.cells[other];
                    stream.accept(light((long) side | (quad & ~LIGHT) | (neighbor & LIGHT), metadata));
                }
            }
            faces = ~faces;
            while (faces != 0) {
                int index = Integer.numberOfTrailingZeros(faces);
                faces &= ~Integer.lowestOneBit(faces);
                int skipped;
                if (index < 11) skipped = (int) (low >> (index * 5));
                else if (index < 22) skipped = (int) (middle >> ((index - 11) * 5));
                else skipped = (int) (high >> ((index - 22) * 5));
                skipped &= 31;
                if (skipped != 0) xPlanes[index].skip(skipped);
            }
        }
    }
    private boolean outerXVisible(long raw, int oppositeFace) {
        int block = VoxelBits.block(raw);
        if (block == 0) return true;
        long metadata = models.metadata(models.modelId(block));
        if (ModelMetadata.fullyOpaque(metadata)) return false;
        return !ModelMetadata.faceOccludes(metadata, oppositeFace);
    }
    void xOuter() {
        var negative = xPlanes[0];
        var positive = xPlanes[31];
        negative.finish(); positive.finish();
        negative.offset = false; positive.offset = false;
        for (int y = 0; y < 32; y++) {
            int skipNegative = 0, skipPositive = 0;
            for (int z = 0; z < 32; z++) {
                int row = y * 32 + z, occupied = samples.opaque[row];
                if ((occupied & 1) != 0) {
                    long neighbor = samples.neighbors[row];
                    if (outerXVisible(neighbor, 5)) {
                        negative.skip(skipNegative); skipNegative = 0;
                        long quad = samples.cells[row * 64], metadata = samples.cells[row * 64 + 1];
                        negative.accept(light((quad & ~LIGHT) | ((neighbor & (0xFFL << 56)) >>> 1), metadata));
                    } else skipNegative++;
                } else skipNegative++;
                if ((occupied & (1 << 31)) != 0) {
                    long neighbor = samples.neighbors[row + 1024];
                    if (outerXVisible(neighbor, 4)) {
                        positive.skip(skipPositive); skipPositive = 0;
                        long quad = samples.cells[(row * 32 + 31) * 2], metadata = samples.cells[(row * 32 + 31) * 2 + 1];
                        positive.accept(light(1L | (quad & ~LIGHT) | ((neighbor & (0xFFL << 56)) >>> 1), metadata));
                    } else skipPositive++;
                } else skipPositive++;
            }
            negative.skip(skipNegative); positive.skip(skipPositive);
        }
        negative.finish(); positive.finish();
        negative.offset = true; positive.offset = true;
    }
}

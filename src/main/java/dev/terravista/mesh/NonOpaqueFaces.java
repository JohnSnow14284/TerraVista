package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.world.VoxelBits;
import static dev.terravista.mesh.FaceEmission.LIGHT;
import static dev.terravista.mesh.FaceEmission.light;
import static dev.terravista.mesh.FaceEmission.nonOpaque;

/** 使用成对流生成非不透明面并分别处理内部与边界光照。 */
final class NonOpaqueFaces {
    private static final long LANES = 0x4210842108421L;
    private final SectionSamples samples;
    private final ModelCatalog models;
    private final PackedQuadWriter.Plane positive;
    private final PackedQuadWriter.Plane negative;
    private final PackedQuadWriter.Plane[] xPositive;
    private final PackedQuadWriter.Plane[] xNegative;

    NonOpaqueFaces(SectionSamples samples, ModelCatalog models,
                   PackedQuadWriter.Plane positive, PackedQuadWriter.Plane negative,
                   PackedQuadWriter.Plane[] xPositive, PackedQuadWriter.Plane[] xNegative) {
        this.samples = samples; this.models = models; this.positive = positive; this.negative = negative;
        this.xPositive = xPositive; this.xNegative = xNegative;
    }
    void yzInner(int axis) {
        negative.offset = false;
        positive.axis = axis; negative.axis = axis;
        for (int layer = 1; layer < 31; layer++) {
            positive.position = layer; negative.position = layer;
            int skipped = 0;
            for (int row = 0; row < 32; row++) {
                int index = axis == 0 ? layer * 32 + row : row * 32 + layer;
                int stride = axis == 0 ? 1024 : 32;
                int faces = samples.nonOpaque[index];
                if (faces == 0) { skipped += 32; continue; }
                positive.skip(skipped); negative.skip(skipped); skipped = 0;
                int previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) { positive.skip(gap); negative.skip(gap); }
                    faces &= ~Integer.lowestOneBit(faces);
                    int cell = x + index * 32;
                    long quad = samples.cells[cell * 2], metadata = samples.cells[cell * 2 + 1];
                    nonOpaque(axis << 1, quad, metadata, samples.cells[(cell - stride) * 2], samples.cells[(cell - stride) * 2 + 1], negative);
                    nonOpaque((axis << 1) | 1, quad, metadata, samples.cells[(cell + stride) * 2], samples.cells[(cell + stride) * 2 + 1], positive);
                }
                positive.endRow(); negative.endRow();
            }
            positive.finish(); negative.finish();
        }
    }
    void yzOuter(int axis) {
        negative.offset = false;
        positive.axis = axis; negative.axis = axis;
        for (int side = 0; side < 2; side++) {
            int layer = side == 0 ? 0 : 31, stride = (axis == 0 ? 1024 : 32) * (1 - side * 2);
            positive.position = layer; negative.position = layer;
            int skipped = 0;
            for (int row = 0; row < 32; row++) {
                int index = axis == 0 ? layer * 32 + row : row * 32 + layer;
                int faces = samples.nonOpaque[index];
                if (faces == 0) { skipped += 32; continue; }
                positive.skip(skipped); negative.skip(skipped); skipped = 0;
                int previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) { positive.skip(gap); negative.skip(gap); }
                    faces &= ~Integer.lowestOneBit(faces);
                    int cell = x + index * 32;
                    long neighbor = samples.neighbors[(axis + 1) * 2048 + side * 1024 + row * 32 + x];
                    long quad = samples.cells[cell * 2], metadata = samples.cells[cell * 2 + 1];
                    long outerQuad = 0, outerMetadata = 0;
                    if (VoxelBits.block(neighbor) != 0) {
                        int model = models.modelId(VoxelBits.block(neighbor));
                        outerQuad = (long) model << 26;
                        outerMetadata = models.metadata(model);
                    }
                    long innerQuad = samples.cells[(cell + stride) * 2], innerMetadata = samples.cells[(cell + stride) * 2 + 1];
                    if (FaceEmission.visible((axis << 1) | 1, quad, metadata,
                            side == 1 ? outerQuad : innerQuad, side == 1 ? outerMetadata : innerMetadata))
                        positive.accept(light(1L | quad, metadata));
                    else positive.skip(1);
                    if (FaceEmission.visible(axis << 1, quad, metadata,
                            side == 0 ? outerQuad : innerQuad, side == 0 ? outerMetadata : innerMetadata))
                        negative.accept(light(quad, metadata));
                    else negative.skip(1);
                }
                positive.endRow(); negative.endRow();
            }
            positive.finish(); negative.finish();
        }
    }
    void xInner() {
        for (int y = 0; y < 32; y++) {
            long low = 0, middle = 0, high = 0;
            int neverUsed = -1, faces = 0;
            for (int z = 0; z < 32; z++) {
                faces = samples.nonOpaque[y * 32 + z] & ~0x80000001;
                low += LANES; middle += LANES; high += LANES;
                neverUsed &= ~faces;
                if (z == 30 && neverUsed != 0) {
                    int pending = neverUsed;
                    while (pending != 0) {
                        int index = Integer.numberOfTrailingZeros(pending);
                        pending &= ~Integer.lowestOneBit(pending);
                        xPositive[index].skip(31); xNegative[index].skip(31);
                    }
                    low &= ~(Long.expand(Integer.toUnsignedLong(neverUsed), LANES) * 0x1F);
                    middle &= ~(Long.expand(Integer.toUnsignedLong(neverUsed) >> 11, LANES) * 0x1F);
                    high &= ~(Long.expand(Integer.toUnsignedLong(neverUsed) >> 22, LANES) * 0x1F);
                }
                if (faces == 0) continue;
                int pending = faces;
                while (pending != 0) {
                    int index = Integer.numberOfTrailingZeros(pending);
                    pending &= ~Integer.lowestOneBit(pending);
                    int skipped;
                    if (index < 11) {
                        skipped = (int) (low >> (index * 5)); low &= ~(0x1FL << (index * 5));
                    } else if (index < 22) {
                        skipped = (int) (middle >> ((index - 11) * 5)); middle &= ~(0x1FL << ((index - 11) * 5));
                    } else {
                        skipped = (int) (high >> ((index - 22) * 5)); high &= ~(0x1FL << ((index - 22) * 5));
                    }
                    skipped = (skipped & 31) - 1;
                    var forward = xPositive[index]; var backward = xNegative[index];
                    if (skipped != 0) { forward.skip(skipped); backward.skip(skipped); }
                    int cell = index + z * 32 + y * 1024;
                    long quad = samples.cells[cell * 2], metadata = samples.cells[cell * 2 + 1];
                    nonOpaque(4, quad, metadata, samples.cells[(cell - 1) * 2], samples.cells[(cell - 1) * 2 + 1], backward);
                    nonOpaque(5, quad, metadata, samples.cells[(cell + 1) * 2], samples.cells[(cell + 1) * 2 + 1], forward);
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
                if (skipped != 0) { xPositive[index].skip(skipped); xNegative[index].skip(skipped); }
            }
        }
    }
    static void dualX(int side, long quad, long metadata, int outerModel, int outerLight, long outerMetadata,
                      long innerQuad, long innerMetadata, PackedQuadWriter.Plane outer, PackedQuadWriter.Plane inner) {
        int outerFace = 4 ^ side;
        if ((outerModel == 0 && ModelMetadata.faceExists(metadata, outerFace))
                || (outerModel != 0 && FaceEmission.visible(outerFace, quad, metadata, (long) outerModel << 26, outerMetadata))) {
            outer.accept(light((long) side | (quad & ~LIGHT)
                    | (ModelMetadata.faceUsesSelfLighting(metadata, outerFace) ? quad : (long) outerLight << 55), metadata));
        } else outer.skip(1);
        int innerFace = 5 ^ side;
        if (FaceEmission.visible(innerFace, quad, metadata, innerQuad, innerMetadata)) {
            inner.accept(light((long) (side ^ 1) | (quad & ~LIGHT)
                    | ((ModelMetadata.faceUsesSelfLighting(metadata, innerFace) ? quad : innerQuad) & LIGHT), metadata));
        } else inner.skip(1);
    }
    private void outerXCell(int row, int side, PackedQuadWriter.Plane outward, PackedQuadWriter.Plane inward, int skipped) {
        long neighbor = samples.neighbors[row + side * 1024];
        int cell = (row * 32 + (side == 0 ? 0 : 31)) * 2;
        long quad = samples.cells[cell], metadata = samples.cells[cell + 1];
        int model = 0; long neighborMetadata = 0;
        if (VoxelBits.block(neighbor) != 0) {
            model = models.modelId(VoxelBits.block(neighbor)); neighborMetadata = models.metadata(model);
        }
        // 两端均先跳过负向流，再跳过正向流。
        if (side == 0) { outward.skip(skipped); inward.skip(skipped); }
        else { inward.skip(skipped); outward.skip(skipped); }
        int adjacent = cell + (side == 0 ? 2 : -2);
        dualX(side, quad, metadata, model, VoxelBits.light(neighbor), neighborMetadata,
                samples.cells[adjacent], samples.cells[adjacent + 1], outward, inward);
    }
    void xOuter() {
        var leftPositive = xPositive[0]; leftPositive.finish();
        var leftNegative = xNegative[0]; leftNegative.finish();
        var rightPositive = xPositive[31]; rightPositive.finish();
        var rightNegative = xNegative[31]; rightNegative.finish();
        for (int y = 0; y < 32; y++) {
            int skipLeft = 0, skipRight = 0;
            for (int z = 0; z < 32; z++) {
                int row = y * 32 + z, faces = samples.nonOpaque[row];
                if ((faces & 1) != 0) { outerXCell(row, 0, leftNegative, leftPositive, skipLeft); skipLeft = 0; }
                else skipLeft++;
                if ((faces & (1 << 31)) != 0) { outerXCell(row, 1, rightPositive, rightNegative, skipRight); skipRight = 0; }
                else skipRight++;
            }
            leftNegative.skip(skipLeft); leftPositive.skip(skipLeft);
            rightNegative.skip(skipRight); rightPositive.skip(skipRight);
        }
    }
}

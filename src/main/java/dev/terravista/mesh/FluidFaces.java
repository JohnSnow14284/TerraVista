package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.model.PendingModel;
import dev.terravista.world.VoxelBits;
import static dev.terravista.mesh.FaceEmission.LIGHT;
import static dev.terravista.mesh.FaceEmission.light;
import static dev.terravista.mesh.FaceEmission.typing;

/** 按顺序生成流体与含流体方块的表面。 */
final class FluidFaces {
    private static final long LANES = 0x4210842108421L;
    private final SectionSamples samples;
    private final ModelCatalog models;
    private final FluidSurface surface;
    private final PackedQuadWriter.Plane plane;
    private final PackedQuadWriter.Plane[] xPlanes;

    FluidFaces(SectionSamples samples, ModelCatalog models, FluidSurface surface,
               PackedQuadWriter.Plane plane, PackedQuadWriter.Plane[] xPlanes) {
        this.samples = samples; this.models = models; this.surface = surface;
        this.plane = plane; this.xPlanes = xPlanes;
    }
    void yzInner(int axis) {
        for (int layer = 0; layer < 31; layer++) {
            plane.position = layer;
            int skipped = 0;
            for (int row = 0; row < 32; row++) {
                int index = axis == 0 ? layer * 32 + row : row * 32 + layer, stride = axis == 0 ? 32 : 1;
                int current = samples.fluid[index], next = samples.fluid[index + stride];
                int faces;
                if (axis == 0) faces = (current & ~next) | (next & ~current & ~samples.opaque[index]);
                else {
                    faces = (current | samples.opaque[index]) ^ (next | samples.opaque[index + stride]);
                    faces &= current | next;
                }
                if (faces == 0) { skipped += 32; continue; }
                plane.skip(skipped); skipped = 0;
                int forward = faces & current, previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) plane.skip(gap);
                    faces &= ~Integer.lowestOneBit(faces);
                    int side = (forward >> x) & 1;
                    int cell = x + index * 32, a = cell * 2, b = (cell + stride * 32) * 2;
                    int self = side == 1 ? a : b, other = side == 1 ? b : a;
                    boolean top = axis == 0 && side == 1;
                    if (top && VoxelBits.iceWater(surface.source().contents().blocks()[other / 2])) {
                        plane.skip(1); continue;
                    }
                    if (axis != 0 && !VoxelBits.sampled(surface.source().contents().blocks()[other / 2])) {
                        plane.skip(1); continue;
                    }
                    if (!top && ModelMetadata.faceOccludes(samples.cells[other + 1], (axis << 1) | (1 - side))) {
                        plane.skip(1); continue;
                    }
                    long quad = samples.cells[self], metadata = samples.cells[self + 1];
                    if (ModelMetadata.containsFluid(metadata)) {
                        int model = (int) ((quad >> 26) & 0xFFFF);
                        quad &= ~(0xFFFFL << 26);
                        int fluid = models.fluidModel(model);
                        quad |= Integer.toUnsignedLong(fluid) << 26;
                        metadata = models.metadata(fluid);
                        quad = (quad & ~0b110L) | typing(metadata);
                    }
                    long neighbor = samples.cells[other];
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
                int faces = samples.fluid[index];
                if (faces == 0) { skipped += 32; continue; }
                plane.skip(skipped); skipped = 0;
                int previous = -1;
                while (faces != 0) {
                    int x = Integer.numberOfTrailingZeros(faces), gap = x - previous - 1;
                    previous = x;
                    if (gap != 0) plane.skip(gap);
                    faces &= ~Integer.lowestOneBit(faces);
                    int cell = x + index * 32;
                    long neighbor = samples.neighbors[(axis + 1) * 2048 + side * 1024 + row * 32 + x];
                    if (axis == 0 && side == 1 && VoxelBits.iceWater(neighbor)) { plane.skip(1); continue; }
                    if (axis != 0 && !VoxelBits.sampled(neighbor)) { plane.skip(1); continue; }
                    long raw = surface.source().contents().blocks()[cell];
                    if (surface.sameFluid(raw, neighbor)) { plane.skip(1); continue; }
                    long quad = samples.cells[cell * 2], metadata = samples.cells[cell * 2 + 1];
                    if (ModelMetadata.containsFluid(metadata)) {
                        int model = (int) ((quad >> 26) & 0xFFFF);
                        quad &= ~(0xFFFFL << 26);
                        int fluid = models.fluidModel(model);
                        quad |= Integer.toUnsignedLong(fluid) << 26;
                        metadata = models.metadata(fluid);
                        quad = (quad & ~0b110L) | typing(metadata);
                    }
                    if (VoxelBits.block(neighbor) != 0) {
                        int model = models.modelId(VoxelBits.block(neighbor));
                        long neighborMetadata = models.metadata(model);
                        boolean top = axis == 0 && side == 1;
                        if (!top && ModelMetadata.faceOccludes(neighborMetadata, (axis << 1) | (1 - side))) {
                            plane.skip(1); continue;
                        }
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
                int solid = samples.opaque[y * 32 + z], fluidMask = samples.fluid[y * 32 + z], occupied = solid | fluidMask;
                faces = (occupied ^ (occupied >>> 1)) & (-1 >>> 1);
                faces &= fluidMask | (fluidMask >> 1);
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
                    int side = (forward >> index) & 1, cell = index + z * 32 + y * 1024;
                    int self = (cell + 1 - side) * 2, other = (cell + side) * 2;
                    if (!VoxelBits.sampled(surface.source().contents().blocks()[other / 2])) {
                        stream.skip(1); continue;
                    }
                    if (ModelMetadata.faceOccludes(samples.cells[other + 1], (2 << 1) | (1 - side))) {
                        stream.skip(1); continue;
                    }
                    long quad = samples.cells[self], metadata = samples.cells[self + 1];
                    if (ModelMetadata.containsFluid(metadata)) {
                        int model = (int) ((quad >> 26) & 0xFFFF);
                        quad &= ~(0xFFFFL << 26);
                        int fluid = models.fluidModel(model);
                        quad |= Integer.toUnsignedLong(fluid) << 26;
                        metadata = models.metadata(fluid);
                        quad = (quad & ~0b110L) | typing(metadata);
                    }
                    long neighbor = samples.cells[other];
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
    private int outerXCell(int row, int side, PackedQuadWriter.Plane stream, int skipped) {
        long neighbor = samples.neighbors[row + side * 1024];
        if (!VoxelBits.sampled(neighbor)) return skipped + 1;
        boolean visible = true;
        int cell = (row * 32 + (side == 0 ? 0 : 31)) * 2;
        long raw = surface.source().contents().blocks()[cell >> 1];
        long quad = samples.cells[cell], metadata = samples.cells[cell + 1];
        if (surface.sameFluid(raw, neighbor)) visible = false;
        if (ModelMetadata.containsFluid(metadata)) {
            int model = (int) ((quad >> 26) & 0xFFFF);
            quad &= ~(0xFFFFL << 26);
            int fluid = models.fluidModel(model);
            quad |= Integer.toUnsignedLong(fluid) << 26;
            metadata = models.metadata(fluid);
            quad = (quad & ~0b110L) | typing(metadata);
        }
        if (VoxelBits.block(neighbor) != 0) {
            int model = models.modelId(VoxelBits.block(neighbor));
            long neighborMetadata = models.metadata(model);
            if (ModelMetadata.fullyOpaque(neighborMetadata)) visible = false;
            if (ModelMetadata.faceOccludes(neighborMetadata, (2 << 1) | (1 - side))) visible = false;
        }
        if (!visible) return skipped + 1;
        stream.skip(skipped);
        long lighting = (neighbor & (0xFFL << 56)) >>> 1;
        stream.accept(light((long) side | (quad & ~LIGHT) | lighting, metadata));
        return 0;
    }
    void xOuter() {
        var negative = xPlanes[0];
        var positive = xPlanes[31];
        negative.finish(); positive.finish();
        negative.offset = false; positive.offset = false;
        for (int y = 0; y < 32; y++) {
            int skipNegative = 0, skipPositive = 0;
            for (int z = 0; z < 32; z++) {
                int row = y * 32 + z, faces = samples.fluid[row];
                skipNegative = (faces & 1) != 0 ? outerXCell(row, 0, negative, skipNegative) : skipNegative + 1;
                skipPositive = (faces & (1 << 31)) != 0 ? outerXCell(row, 1, positive, skipPositive) : skipPositive + 1;
            }
            negative.skip(skipNegative); positive.skip(skipPositive);
        }
        negative.finish(); positive.finish();
        negative.offset = true; positive.offset = true;
    }
    void prepareIce() {
        long[] raw = surface.source().contents().blocks();
        int level = surface.source().contents().level;
        int model = -1;
        plane.axis = 0;
        plane.offset = false;
        for (int row = 0; row < 1056; row++) {
            int candidates = row < 1024 ? samples.nonOpaque[row]
                    : samples.opaque[row - 32] | samples.nonOpaque[row - 32];
            while (candidates != 0) {
                int cell = row * 32 + Integer.numberOfTrailingZeros(candidates);
                candidates &= candidates - 1;
                long ice = cell < 32768 ? raw[cell] : samples.neighbors[3 * 1024 + cell - 32768];
                if (!surface.ice(ice)) continue;
                int height = VoxelBits.surfaceHeight(ice);
                if (cell >= 1024 && (level == 0 || height <= (256 >> level))) shadeGround(cell - 1024);
                if (cell >= 32768) continue;
                if (!VoxelBits.iceWater(raw[cell])) continue;
                if (model == -1) {
                    int block = surface.waterBlockId();
                    if (!models.hasModel(block)) throw new PendingModel(block, true);
                    model = models.modelId(block);
                }
                long metadata = models.metadata(model);
                long quad = FaceEmission.attributes(model, raw[cell], metadata) | 1;
                plane.position = cell >>> 10;
                plane.emit(cell & 31, (cell >>> 5) & 31, 1, 1, light(quad, metadata));
            }
        }
        plane.offset = true;
    }
    private void shadeGround(int cell) {
        long metadata = samples.cells[cell * 2 + 1];
        if (metadata == 0 || ModelMetadata.translucent(metadata) || ModelMetadata.fluid(metadata)
                || ModelMetadata.containsFluid(metadata) || ModelMetadata.doubleSided(metadata)) return;
        long data = samples.cells[cell * 2];
        int height = (int) (data >>> 3) & 511;
        if (height != 0 && height != 256) return;
        float shade = surface.iceShade(cell & 31, cell >>> 10, (cell >>> 5) & 31, false);
        if (shade >= 1) return;
        int colour = 0xFFFFFF;
        int slot = (int) (data >>> 46) & 511;
        if (data < 0) colour = models.blendedTints.colour(slot);
        else if (ModelMetadata.biomeColoured(metadata)) {
            int model = (int) (data >>> 26) & 65535;
            int[] colours = models.blendedTints.row(model);
            if (colours == null || slot >= colours.length) throw new PendingModel(model, false);
            colour = colours[slot];
        }
        int tint = models.blendedTints.shadedIndex(colour, Math.round(shade * 255));
        samples.cells[cell * 2] = (data & ~(511L << 46)) | ((long) tint << 46) | Long.MIN_VALUE;
    }
}

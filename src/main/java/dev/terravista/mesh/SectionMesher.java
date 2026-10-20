package dev.terravista.mesh;

import dev.terravista.model.ModelCatalog;
import dev.terravista.model.PendingModel;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.VoxelWorld;

/** 管理单个工作线程的区块网格生命周期与材质流。 */
public final class SectionMesher {
    private static final boolean BUILD_OCCUPANCY = false;
    private final SectionSamples samples;
    private final FluidSurface fluids;
    private final BiomeBorderBlend biomeBlend;
    private final PackedQuadWriter writer;
    private final PackedQuadWriter.Plane positive;
    private final PackedQuadWriter.Plane negative;
    private final PackedQuadWriter.Plane[] xPositive = new PackedQuadWriter.Plane[32];
    private final PackedQuadWriter.Plane[] xNegative = new PackedQuadWriter.Plane[32];
    private final OpaqueFaces opaqueFaces;
    private final FluidFaces fluidFaces;
    private final NonOpaqueFaces nonOpaqueFaces;
    private final TerrainBorders terrainBorders;
    private final OccupancyWords occupancy;

    public SectionMesher(VoxelWorld world, ModelCatalog models, boolean emitMeshlets) {
        this(world, models, emitMeshlets, false);
    }
    public SectionMesher(VoxelWorld world, ModelCatalog models, boolean emitMeshlets, boolean generateOccupancy) {
        samples = new SectionSamples(world, models);
        fluids = new FluidSurface(world);
        biomeBlend = new BiomeBorderBlend(world, models);
        writer = new PackedQuadWriter(models, fluids);
        positive = writer.plane(); negative = writer.plane();
        for (int index = 0; index < 32; index++) {
            var stream = writer.plane(); stream.position = index; stream.axis = 2; xPositive[index] = stream;
        }
        for (int index = 0; index < 32; index++) {
            var stream = writer.plane(); stream.position = index; stream.axis = 2; stream.offset = false; xNegative[index] = stream;
        }
        opaqueFaces = new OpaqueFaces(samples, models, positive, xPositive);
        fluidFaces = new FluidFaces(samples, models, fluids, positive, xPositive);
        nonOpaqueFaces = new NonOpaqueFaces(samples, models, positive, negative, xPositive, xNegative);
        terrainBorders = new TerrainBorders(samples, models, fluids, writer);
        occupancy = generateOccupancy && BUILD_OCCUPANCY ? OccupancyWords.blocks32() : null;
    }
    private void reset() {
        writer.resetCount();
        positive.reset(); positive.offset = true;
        negative.reset(); negative.offset = true;
        for (var stream : xPositive) { stream.reset(); stream.offset = true; }
        for (var stream : xNegative) { stream.reset(); stream.offset = false; }
        if (occupancy != null) occupancy.reset();
        writer.resetBounds();
        writer.resetPartitions();
        samples.resetMasks();
        fluids.clearCache();
    }
    private void yzFaces() {
        for (int axis = 0; axis < 2; axis++) {
            positive.axis = axis;
            opaqueFaces.yzInner(axis); opaqueFaces.yzOuter(axis);
            fluidFaces.yzInner(axis); fluidFaces.yzOuter(axis);
            nonOpaqueFaces.yzInner(axis); nonOpaqueFaces.yzOuter(axis);
        }
    }
    private void xFaces() {
        opaqueFaces.xInner(); opaqueFaces.xOuter();
        for (var stream : xPositive) stream.finish();
        fluidFaces.xInner(); fluidFaces.xOuter();
        for (var stream : xPositive) stream.finish();
        nonOpaqueFaces.xInner(); nonOpaqueFaces.xOuter();
        for (var stream : xPositive) stream.finish();
        for (var stream : xNegative) stream.finish();
    }
    /** 由调用方持有并释放源区块引用。 */
    public SectionMesh generate(ResidentSection section) {
        reset();
        int acquired = samples.prepare(section.contents().blocks());
        if ((acquired & (1 << 31)) != 0) throw new PendingModel(acquired & ((1 << 20) - 1), true);
        int neighbors = acquired & 0b11_11_11, flags = acquired >>> 6;
        samples.acquireFaces(section, neighbors);
        fluids.source(section);
        try {
            if (section.contents().level > 0) samples.closeCoveredSurfaces(section.contents().blocks());
            biomeBlend.apply(section, samples);
            fluidFaces.prepareIce();
            yzFaces(); xFaces();
            if (section.contents().level > 0) terrainBorders.emit(section);
        } catch (PendingModel pending) {
            pending.retryMask = neighbors;
            pending.retryData = samples.neighbors;
            throw pending;
        } finally { fluids.releaseNeighbors(); }
        if (occupancy != null && section.contents().level == 0 && writer.count() != 0 && (flags & 1) != 0)
            samples.collectOccupancy(occupancy);
        return writer.finish(section, occupancy);
    }
    public void free() { writer.free(); }
}

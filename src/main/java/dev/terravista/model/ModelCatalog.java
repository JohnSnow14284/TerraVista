package dev.terravista.model;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.config.ClientOptions;
import dev.terravista.world.BiomePaletteEntry;
import dev.terravista.world.StatePalette;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

/** 协调模型捕获、网格发布与设备上传队列。 */
public final class ModelCatalog {
    private record Bake(int blockId, BlockState state) {}
    private final Biome defaultBiome = Minecraft.getInstance().level.registryAccess()
            .lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS);
    public final ModelCapture capture;
    private final long scratch = MemoryUtil.nmemAlloc(16 * 16 * 8 * 6);
    private final long[] metadata;
    private final int[] fluidModels;
    private final int[] blockModels;
    private final ModelDeduplication models = new ModelDeduplication();
    private final IntOpenHashSet inFlight = new IntOpenHashSet();
    private final ReentrantLock flightLock = new ReentrantLock();
    private final StatePalette palette;
    private final ModelStore storage;
    private final BiomeColours colours;
    public final BlendedTints blendedTints = new BlendedTints();
    private final ConcurrentLinkedDeque<Bake> bakes = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ModelUpload> uploads = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<BiomePaletteEntry> biomes = new ConcurrentLinkedDeque<>();
    private Object2IntMap<BlockState> customIds;
    private final ClientOptions.FoliageStyle foliageStyle = ClientOptions.CONFIG.foliage();

    public ModelCatalog(StatePalette palette, ModelStore storage) {
        this.palette = palette;
        this.storage = storage;
        colours = new BiomeColours(defaultBiome, blendedTints);
        capture = new ModelCapture();
        capture.setupTexture();
        metadata = new long[1 << 16];
        fluidModels = new int[1 << 16];
        blockModels = new int[1 << 20];
        Arrays.fill(blockModels, -1);
        Arrays.fill(fluidModels, -1);
        request(0);
    }
    public void customIds(Object2IntMap<BlockState> mapping) { customIds = mapping; }
    public boolean request(int blockId) {
        if (blockModels[blockId] != -1) return false;
        var state = palette.blockState(blockId);
        if (state.getBlock() instanceof StairBlock stairs)
            state = stairs.baseState.getBlock().withPropertiesOf(state);
        boolean fluid = state.getBlock() instanceof LiquidBlock;
        if (!fluid && !state.getFluidState().isEmpty()) {
            int fluidId = palette.blockId(state.getFluidState().createLegacyBlock());
            if (blockModels[fluidId] == -1) request(fluidId);
        }
        flightLock.lock();
        try {
            if (!inFlight.add(blockId)) return false;
            VarHandle.loadLoadFence();
            if (blockModels[blockId] != -1) { inFlight.remove(blockId); return false; }
            bakes.add(new Bake(blockId, state));
            return true;
        } finally { flightLock.unlock(); }
    }
    private boolean captureNext() {
        var bake = bakes.poll();
        if (bake == null) return false;
        int flags = capture.render(bake.state, scratch);
        var faces = new ModelFacePixels[6];
        for (int face = 0; face < 6; face++) {
            long source = scratch + 256L * 8 * face;
            int[] pixels = new int[256], depth = new int[256];
            for (int pixel = 0; pixel < 256; pixel++) {
                long value = MemoryUtil.memGetLong(source + pixel * 8L);
                pixels[pixel] = (int) value;
                depth[pixel] = (int) (value >>> 32);
            }
            faces[face] = new ModelFacePixels(pixels, depth, 16, 16);
        }
        ChunkSectionLayer layer = null;
        if ((flags & 4) != 0) {
            boolean translucent = false;
            for (var face : faces) {
                translucent |= FaceCoverage.translucentWhereWritten(face);
                if (translucent) break;
            }
            if (translucent) layer = ChunkSectionLayer.TRANSLUCENT;
            else {
                boolean solid = true;
                for (var face : faces) {
                    solid &= FaceCoverage.opaqueWhereWritten(face);
                    if (!solid) break;
                }
                layer = solid ? ChunkSectionLayer.SOLID : ChunkSectionLayer.CUTOUT;
            }
        }
        if (layer == null && (flags & 8) != 0) layer = ChunkSectionLayer.CUTOUT;
        if (bake.state.is(BlockTags.LEAVES)) layer = foliageStyle == ClientOptions.FoliageStyle.DENSE
                ? ChunkSectionLayer.SOLID : ChunkSectionLayer.CUTOUT;
        if (layer == null) layer = ChunkSectionLayer.SOLID;
        var result = assemble(bake.blockId, bake.state, faces, (flags & 1) != 0, (flags & 2) != 0, layer, (flags & 16) != 0);
        if (result != null) uploads.add(result);
        return true;
    }
    private BakedModelUpload assemble(int blockId, BlockState state, ModelFacePixels[] faces,
                                      boolean shaded, boolean darkened, ChunkSectionLayer layer, boolean crossPlant) {
        if (blockModels[blockId] != -1)
            throw new IllegalStateException("TerraVista Models: Block id already added: " + blockId + " for state: " + state);
        flightLock.lock();
        if (!inFlight.contains(blockId)) {
            flightLock.unlock();
            throw new IllegalStateException("TerraVista Models: Bake result has no pending block-state request");
        }
        flightLock.unlock();
        boolean fluid = state.getBlock() instanceof LiquidBlock;
        int fluidModel = -1;
        if (!fluid && !state.getFluidState().isEmpty()) {
            int fluidId = palette.blockId(state.getFluidState().createLegacyBlock());
            fluidModel = blockModels[fluidId];
            if (fluidModel == -1)
                throw new IllegalStateException("TerraVista Models: Required fluid model has not been baked");
        }
        var sources = ModelTinting.sources(state);
        boolean biomeDependent = false;
        if (sources != null) biomeDependent = ModelTinting.biomeDependent(sources, state);
        int foliage = state.is(BlockTags.LEAVES) ? foliageStyle.ordinal() + 1 : 0;
        boolean waterDatum = fluid && state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
        var key = new ModelDeduplication.Key(faces, fluidModel, biomeDependent || sources == null
                ? -1 : ModelTinting.constant(sources, state, defaultBiome) | 0xFF000000, foliage, waterDatum, crossPlant);
        int modelId = models.find(key);
        if (modelId != -1) {
            blockModels[blockId] = modelId;
            retire(blockId, null);
            return null;
        }
        modelId = models.insert(key);
        if (fluid) fluidModels[modelId] = modelId;
        else if (fluidModel != -1) fluidModels[modelId] = fluidModel;
        int check = layer == ChunkSectionLayer.SOLID ? FaceCoverage.STENCIL : FaceCoverage.ALPHA;
        var result = new BakedModelUpload();
        try {
            result.modelId = modelId;
            if (!fluid && !state.getFluidState().isEmpty() && fluidModel != -1)
                biomeDependent |= ModelMetadata.biomeColoured(metadata(fluidModel));
            var depths = ModelPacking.depths(faces, check, layer != ChunkSectionLayer.SOLID
                    ? FaceTextureEncoding.MINIMUM : FaceTextureEncoding.AVERAGE);
            if (crossPlant) CrossPlantGeometry.center(depths);
            boolean doubleSided = ModelPacking.doubleSided(depths);
            boolean allTrue = true, allFalse = true;
            for (var direction : Direction.values()) {
                if (state.skipRendering(state, direction)) allFalse = false;
                else allTrue = false;
            }
            boolean selfCulls = false;
            if (allFalse == allTrue) selfCulls = false;
            if (allTrue) selfCulls = true;
            if (foliage != 0) selfCulls = foliageStyle != ClientOptions.FoliageStyle.DETAILED;
            long header = ModelPacking.header(biomeDependent, layer, doubleSided,
                    !fluid && !state.getFluidState().isEmpty(), fluid, selfCulls);
            metadata[modelId] = ModelPacking.faces(result.model.address, faces, depths, check, layer, header, sources != null, state);
            MemoryUtil.memPutInt(result.model.address + 24, ModelPacking.flags(sources != null, biomeDependent, layer, shaded, fluid, doubleSided)
                    | (foliage << 6) | (waterDatum ? 1 << 8 : 0));
            if (sources == null) MemoryUtil.memPutInt(result.model.address + 28, -1);
            else if (!biomeDependent) MemoryUtil.memPutInt(result.model.address + 28, key.tintColour());
            else colours.attach(result, modelId, state, sources);
            MemoryUtil.memPutInt(result.model.address + 32,
                    customIds != null && customIds.containsKey(state) ? customIds.getInt(state) : 0);
            TextureMipmaps.write(darkened, faces, result.texture);
            blockModels[blockId] = modelId;
            retire(blockId, "TerraVista Models: Completed bake has no pending block-state request");
            return result;
        } catch (RuntimeException | Error exception) {
            result.free();
            throw exception;
        }
    }
    private void retire(int blockId, String message) {
        flightLock.lock();
        try {
            if (!inFlight.remove(blockId)) {
                if (message == null) throw new IllegalStateException();
                throw new IllegalStateException(message);
            }
        } finally { flightLock.unlock(); }
    }
    public void addBiome(BiomePaletteEntry biome) { biomes.add(biome); }
    public boolean process() {
        var entry = biomes.poll();
        if (entry != null) {
            var registry = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME);
            var resolved = registry.get(Identifier.parse(entry.biome));
            if (!resolved.isPresent()) ModLog.warn("Models: Could not find biome: " + entry.biome + " using default");
            var result = colours.add(entry.id, resolved.isPresent() ? resolved.orElseThrow().value() : defaultBiome);
            if (result != null) uploads.add(result);
            return true;
        }
        return captureNext();
    }
    public void upload() {
        boolean blended = blendedTints.upload(storage);
        var result = uploads.poll();
        if (result == null) { if (blended) storage.finish(); return; }
        storage.beginTextures();
        try {
            do {
                try { result.upload(storage); }
                finally { result.free(); }
                result = uploads.poll();
            } while (result != null);
        } finally { storage.endTextures(); }
        storage.finish();
    }
    /** 通过共享数组向网格构建器发布模型数据。 */
    public int[] rawMappings() { return blockModels; }
    public int modelId(int blockId) {
        int model = blockModels[blockId];
        if (model == -1) throw new PendingModel(blockId, true);
        return model;
    }
    public boolean hasModel(int blockId) { return blockModels[blockId] != -1; }
    public int fluidModel(int modelId) {
        int fluid = fluidModels[modelId];
        if (fluid == -1) throw new PendingModel(modelId, false);
        return fluid;
    }
    public long metadata(int modelId) { return metadata[modelId]; }
    public int bakedCount() { return models.size(); }
    public int pendingCount() {
        flightLock.lock();
        try { return inFlight.size() + uploads.size() + biomes.size() + bakes.size(); }
        finally { flightLock.unlock(); }
    }
    public void free() {
        capture.free();
        MemoryUtil.nmemFree(scratch);
        while (!uploads.isEmpty()) uploads.poll().free();
    }
}

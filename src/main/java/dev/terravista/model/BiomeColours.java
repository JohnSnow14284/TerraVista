package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;
import java.util.ArrayList;
import java.util.List;

/** 管理工作线程的稀疏生物群系槽位与模型颜色矩阵。 */
final class BiomeColours {
    private record TintedModel(int id, BlockState state) {}
    private final List<Biome> biomes = new ArrayList<>();
    private final List<TintedModel> models = new ArrayList<>();
    private final Biome fallback;
    private final BlendedTints blended;
    BiomeColours(Biome fallback, BlendedTints blended) {
        this.fallback = java.util.Objects.requireNonNull(fallback); this.blended = blended;
    }

    void attach(BakedModelUpload result, int modelId, BlockState state, List<BlockTintSource> sources) {
        int index = models.size() * biomes.size();
        MemoryUtil.memPutInt(result.model.address + 4 * 6 + 4, index);
        models.add(new TintedModel(modelId, state));
        if (!biomes.isEmpty()) {
            result.biomeIndex = index;
            long pointer = (result.colours = new NativeBuffer(4L * biomes.size())).address;
            for (var biome : biomes) {
                MemoryUtil.memPutInt(pointer, ModelTinting.constant(sources, state, biome != null ? biome : fallback) | 0xFF000000);
                pointer += 4;
            }
            blended.row(modelId, result.colours);
        }
    }
    BiomeColourUpload add(int id, Biome biome) {
        if (biome == null) throw new IllegalStateException("TerraVista Models: Null biome");
        if (id < 0) throw new IllegalArgumentException("TerraVista Models: Negative biome index");
        var previous = id < biomes.size() ? biomes.get(id) : null;
        if (previous != null && previous != biome) throw new IllegalStateException("TerraVista Models: Biome index already occupied");
        if (previous == biome) return null;
        for (int i = biomes.size(); i <= id; i++) biomes.add(null);
        biomes.set(id, biome);
        if (models.isEmpty()) return null;
        var result = new BiomeColourUpload(biomes.size(), models.size());
        int index = 0;
        long modelPointer = result.modelIndices.address;
        for (var model : models) {
            var sources = ModelTinting.sources(model.state);
            if (sources == null) throw new IllegalStateException();
            int base = index++ * biomes.size();
            MemoryUtil.memPutLong(modelPointer, Integer.toUnsignedLong(model.id) | (Integer.toUnsignedLong(base) << 32));
            modelPointer += 8;
            long pointer = result.colours.address + base * 4L;
            for (var entry : biomes) {
                MemoryUtil.memPutInt(pointer, ModelTinting.constant(sources, model.state, entry != null ? entry : fallback) | 0xFF000000);
                pointer += 4;
            }
        }
        blended.rebuild(result, biomes.size());
        return result;
    }
}

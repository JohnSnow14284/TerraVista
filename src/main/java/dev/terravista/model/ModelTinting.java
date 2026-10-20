package dev.terravista.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** 选择模型着色来源并采样虚拟世界颜色。 */
final class ModelTinting {
    private ModelTinting() {}
    static List<BlockTintSource> sources(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock) {
            var source = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state.getFluidState()).tintSource();
            if (source == null) return null;
            return List.of(source);
        }
        var sources = Minecraft.getInstance().getBlockColors().getTintSources(state);
        if (sources.isEmpty()) return null;
        return sources;
    }
    static int constant(List<BlockTintSource> sources, BlockState state, Biome biome) {
        var world = new TintWorld(state, biome, null);
        for (var source : sources) {
            if (source != null) {
                int colour = source.colorInWorld(state, world, BlockPos.ZERO);
                if (colour != -1) return colour;
            }
        }
        return -1;
    }
    static boolean biomeDependent(List<BlockTintSource> sources, BlockState state) {
        boolean[] observed = new boolean[1];
        var world = new TintWorld(state, null, observed);
        for (var source : sources) {
            if (source != null) source.colorInWorld(state, world, BlockPos.ZERO);
        }
        return observed[0];
    }
    private static final class TintWorld implements BlockAndTintGetter {
        private final BlockState state;
        private final Biome biome;
        private final boolean[] observed;
        private TintWorld(BlockState state, Biome biome, boolean[] observed) {
            this.state = state;
            this.biome = biome;
            this.observed = observed;
        }
        @Override public int getBrightness(LightLayer layer, BlockPos position) { return 0; }
        @Override public LevelLightEngine getLightEngine() { return LevelLightEngine.EMPTY; }
        @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
        @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
            if (observed != null) { observed[0] = true; return 0; }
            return resolver.getColor(biome, 0, 0);
        }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public BlockState getBlockState(BlockPos position) { return state; }
        @Override public FluidState getFluidState(BlockPos position) { return state.getFluidState(); }
        @Override public int getHeight() { return 0; }
        @Override public int getMinY() { return 0; }
    }
}

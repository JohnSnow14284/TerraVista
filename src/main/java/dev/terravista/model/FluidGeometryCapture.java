package dev.terravista.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** 在按面选择的虚拟采样环境中生成流体几何。 */
final class FluidGeometryCapture {
    private final QuadVertexCapture opaque;
    private final QuadVertexCapture translucent;
    private final FluidRenderer renderer;
    FluidGeometryCapture(QuadVertexCapture opaque, QuadVertexCapture translucent) {
        this.opaque = opaque;
        this.translucent = translucent;
        renderer = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
    }
    void collect(BlockState state, int face) {
        renderer.tesselate(new SampleWorld(state, face), BlockPos.ZERO, layer -> {
            if (layer == ChunkSectionLayer.TRANSLUCENT) return translucent;
            var stream = opaque.vertices();
            if (layer == ChunkSectionLayer.CUTOUT) stream.defaultMetadata(stream.defaultMetadata() | 1);
            else stream.defaultMetadata(stream.defaultMetadata() & ~1);
            return opaque;
        }, state, state.getFluidState());
        translucent.vertices().defaultMetadata(0);
        opaque.vertices().defaultMetadata(0);
    }
    private static boolean air(BlockPos position, int face) {
        var normal = Direction.from3DDataValue(face).getUnitVec3i();
        int dot = normal.getX() * position.getX() + normal.getY() * position.getY() + normal.getZ() * position.getZ();
        return dot >= 1;
    }
    private final class SampleWorld implements BlockAndTintGetter {
        private final BlockState state;
        private final int face;
        private SampleWorld(BlockState state, int face) { this.state = state; this.face = face; }
        // 以固定光照、高度和空方块实体提供流体采样环境。
        @Override public LevelLightEngine getLightEngine() { return LevelLightEngine.EMPTY; }
        @Override public int getBrightness(LightLayer type, BlockPos position) { return 0; }
        @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
        @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
            var trans = translucent.vertices();
            var solid = opaque.vertices();
            trans.defaultMetadata(trans.defaultMetadata() | 4);
            solid.defaultMetadata(solid.defaultMetadata() | 4);
            return -1;
        }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public BlockState getBlockState(BlockPos position) {
            if (air(position, face)) return Blocks.AIR.defaultBlockState();
            return state;
        }
        @Override public FluidState getFluidState(BlockPos position) {
            if (air(position, face)) return Blocks.AIR.defaultBlockState().getFluidState();
            return state.getFluidState();
        }
        @Override public int getHeight() { return 0; }
        @Override public int getMinY() { return 0; }
    }
}

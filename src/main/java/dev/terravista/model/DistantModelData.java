package dev.terravista.model;

import net.caffeinemc.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

/** Pack-facing, unlit LOD data in the existing model record's final 28 bytes. */
final class DistantModelData {
    static final int COLOURS_OFFSET = 36;
    static final int MATERIAL_OFFSET = 60;

    // Vitrail's EngineDefines DH_BLOCK_* values. These are categories, not pack block.properties IDs.
    static int material(BlockState state) {
        if (state.isAir()) return 14;
        if (state.is(BlockTags.LEAVES)) return 1;
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getFluidState().is(FluidTags.LAVA) ? 6 : 12;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return 3;
        if (state.is(BlockTags.BEACON_BASE_BLOCKS) || state.is(BlockTags.COPPER)) return 4;
        if (state.is(Blocks.GRASS_BLOCK)) return 13;
        if (state.is(BlockTags.DIRT)) return 5;
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)) return 7;
        if (state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)) return 11;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return 8;
        if (state.is(BlockTags.SAND)) return 9;
        if (state.is(BlockTags.TERRACOTTA)) return 10;
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) return 2;
        if (state.getLightEmission() > 0) return 15;
        return 0;
    }

    static void write(long model, ModelFacePixels[] faces, int material) {
        for (int face = 0; face < 6; face++) {
            MemoryUtil.memPutInt(model + COLOURS_OFFSET + face * 4L, colour(faces[face].pixels()));
        }
        MemoryUtil.memPutInt(model + MATERIAL_OFFSET, material);
    }

    /** Alpha-weighted linear RGB; cutout holes do not turn a leaf's surface black or transparent. */
    static int colour(int[] pixels) {
        float red = 0, green = 0, blue = 0;
        int weight = 0, covered = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha == 0) continue;
            red += ColorSRGB.srgbToLinear(pixel & 255) * alpha;
            green += ColorSRGB.srgbToLinear((pixel >>> 8) & 255) * alpha;
            blue += ColorSRGB.srgbToLinear((pixel >>> 16) & 255) * alpha;
            weight += alpha;
            covered++;
        }
        return weight == 0 ? 0 : ColorSRGB.linearToSrgb(red / weight, green / weight,
                blue / weight, weight / covered);
    }

    private DistantModelData() {}
}

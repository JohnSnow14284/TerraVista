package dev.terravista.compat;

import net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;

/** 在加载器确认依赖存在后访问 Lithium 调色板。 */
public final class LithiumPaletteBridge {
    private LithiumPaletteBridge() {}
    public static boolean supports(Palette<BlockState> palette) { return palette instanceof LithiumHashPalette<BlockState>; }
}

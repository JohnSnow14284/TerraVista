package dev.terravista.mixin.sodium;

import dev.terravista.compat.SodiumVisibility;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 转交正常遍历中的可见区域查询。 */
@Mixin(value = VisibleChunkCollector.class, remap = false)
public class VisibleRegionBridge {
    @Redirect(method = "visit", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;getForChunk(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;"), remap = false)
    private RenderRegion terravista$visibleRegion(RenderRegionManager regions, int x, int y, int z) {
        return SodiumVisibility.region(regions, x, y, z);
    }
}

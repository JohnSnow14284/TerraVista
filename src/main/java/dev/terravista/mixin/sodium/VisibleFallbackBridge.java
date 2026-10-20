package dev.terravista.mixin.sodium;

import dev.terravista.compat.SodiumVisibility;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FallbackVisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 转交备用遍历中的可见区块查询。 */
@Mixin(value = FallbackVisibleChunkCollector.class, remap = false)
public class VisibleFallbackBridge {
    @Redirect(method = "visit", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/storage/SectionStorage;getCurrent(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;"), remap = false)
    private RenderSection terravista$visibleSection(SectionStorage sections, int x, int y, int z) {
        return SodiumVisibility.section(sections, x, y, z);
    }
}

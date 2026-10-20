package dev.terravista.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取 Sodium 当前区块渲染管理器。 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public interface SodiumRendererAccess {
    @Accessor("renderSectionManager")
    RenderSectionManager terravista$sections();
}

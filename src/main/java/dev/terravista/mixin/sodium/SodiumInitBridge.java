package dev.terravista.mixin.sodium;

import dev.terravista.compat.SodiumFrameEvents;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 转交 Sodium 渲染器初始化完成事件。 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumInitBridge {
    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private void terravista$rendererReady(CallbackInfo ci) {
        SodiumFrameEvents.initialized();
    }
}

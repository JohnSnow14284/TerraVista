package dev.terravista.mixin.vitrail;

import dev.terravista.render.TerrainPassBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.vitrail.render.PackChain", remap = false)
public abstract class TranslucentStageBridge {
    @Inject(method = "drawBeforeTranslucents", at = @At("RETURN"))
    private static void terravista$water(CallbackInfo callback) { TerrainPassBridge.submitTranslucent(); }
}

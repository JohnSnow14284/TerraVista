package dev.terravista.mixin.minecraft;

import dev.terravista.render.FogReplacement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 Sodium 记录雾参数前转交范围替换。 */
@Mixin(value = FogRenderer.class, priority = 900)
public abstract class FogRenderBridge {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void terravista$fog(Camera camera, int distance, DeltaTracker delta, float darkening,
                                ClientLevel level, CallbackInfoReturnable<FogData> callback) {
        if (FogReplacement.active()) FogReplacement.replace(callback.getReturnValue());
    }
}

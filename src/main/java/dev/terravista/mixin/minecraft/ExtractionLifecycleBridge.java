package dev.terravista.mixin.minecraft;

import dev.terravista.render.ExtractionLifecycle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 转交场景提取器的生命周期注入。 */
@Mixin(LevelExtractor.class)
public abstract class ExtractionLifecycleBridge {
    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void terravista$levelChanged(ClientLevel level, CallbackInfo callback) {
        ExtractionLifecycle.level(levelRenderer, level);
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void terravista$rebuild(CallbackInfo callback) {
        ExtractionLifecycle.rebuild(levelRenderer);
    }
}

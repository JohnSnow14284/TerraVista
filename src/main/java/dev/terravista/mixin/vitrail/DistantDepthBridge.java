package dev.terravista.mixin.vitrail;

import dev.terravista.compat.VitrailDepth;
import dev.terravista.compat.VitrailPackedPrograms;
import dev.terravista.config.ClientOptions;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.dh.DhDepth", remap = false)
public abstract class DistantDepthBridge {
    @Inject(method = {"present", "usable"}, at = @At("HEAD"), cancellable = true)
    private static void terravista$present(CallbackInfoReturnable<Boolean> callback) {
        if (VitrailPackedPrograms.ENABLED) callback.setReturnValue(true);
    }

    @Inject(method = "zRow", at = @At("HEAD"), cancellable = true)
    private static void terravista$row(Vector2f target, CallbackInfoReturnable<Boolean> callback) {
        if (VitrailPackedPrograms.ENABLED) { VitrailDepth.row(target); callback.setReturnValue(true); }
    }

    @Inject(method = "renderDistanceBlocks", at = @At("HEAD"), cancellable = true)
    private static void terravista$distance(CallbackInfoReturnable<Integer> callback) {
        if (VitrailPackedPrograms.ENABLED) callback.setReturnValue(ClientOptions.CONFIG.renderDistanceChunks(
                Minecraft.getInstance().options.getEffectiveRenderDistance()) * 16);
    }
}

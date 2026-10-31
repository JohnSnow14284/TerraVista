package dev.terravista.mixin.vitrail;

import dev.terravista.compat.VitrailFragments;
import dev.terravista.compat.VitrailPackedPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.glsl.ProgramTranslator", remap = false)
public abstract class DistantFragmentBridge {
    @Inject(method = "declared", at = @At("RETURN"), cancellable = true)
    private static void terravista$depth(CallbackInfoReturnable<Object> callback) {
        if (VitrailPackedPrograms.ENABLED) callback.setReturnValue(VitrailFragments.adapt(callback.getReturnValue()));
    }
}

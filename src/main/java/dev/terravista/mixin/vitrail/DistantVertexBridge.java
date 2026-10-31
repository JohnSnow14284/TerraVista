package dev.terravista.mixin.vitrail;

import dev.terravista.compat.PackedDistantVertex;
import dev.terravista.compat.VitrailPackedPrograms;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.glsl.DistantVertex", remap = false)
public abstract class DistantVertexBridge {
    @Inject(method = "prologue", at = @At("RETURN"), cancellable = true)
    private static void terravista$packed(List<String> carried, Set<String> used,
            Map<String, String> synthesized, CallbackInfoReturnable<List<String>> callback) {
        if (VitrailPackedPrograms.ENABLED) {
            callback.setReturnValue(PackedDistantVertex.adapt(callback.getReturnValue(), carried));
        }
    }
}

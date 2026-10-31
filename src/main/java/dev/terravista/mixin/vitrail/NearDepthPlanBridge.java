package dev.terravista.mixin.vitrail;

import dev.terravista.compat.VitrailFragments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.pack.target.SamplerPlan", remap = false)
public abstract class NearDepthPlanBridge {
    @Inject(method = "classify(Ljava/lang/String;)Ldev/vitrail/pack/target/SamplerPlan$Kind;",
            at = @At("HEAD"), cancellable = true)
    private static void terravista$depth(String name, CallbackInfoReturnable<Object> callback) {
        if (VitrailFragments.DEPTH.equals(name)) callback.setReturnValue(VitrailFragments.depthKind());
    }
}

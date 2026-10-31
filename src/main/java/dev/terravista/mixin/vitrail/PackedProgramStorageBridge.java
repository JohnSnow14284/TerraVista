package dev.terravista.mixin.vitrail;

import dev.terravista.compat.VitrailPackedPrograms;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.glsl.PackProgram$Loaded", remap = false)
public abstract class PackedProgramStorageBridge {
    @Unique private List<String> terravista$storage;

    @Inject(method = "storageBlocks", at = @At("RETURN"), cancellable = true)
    private void terravista$storage(CallbackInfoReturnable<List<String>> callback) {
        if (!VitrailPackedPrograms.ENABLED) return;
        if (terravista$storage == null) {
            terravista$storage = VitrailPackedPrograms.distant(this)
                    ? VitrailPackedPrograms.storage(callback.getReturnValue()) : callback.getReturnValue();
        }
        callback.setReturnValue(terravista$storage);
    }
}

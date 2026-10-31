package dev.terravista.mixin.vitrail;

import dev.terravista.compat.VitrailPackedPrograms;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.pack.option.EngineDefines", remap = false)
public abstract class PackedDefinesBridge {
    @Inject(method = "table(Ldev/vitrail/pack/option/EngineDefines$Environment;)Ljava/util/Map;",
            at = @At("RETURN"), cancellable = true)
    private static void terravista$abi(CallbackInfoReturnable<Map<String, String>> callback) {
        if (!VitrailPackedPrograms.ENABLED) return;
        // This table also enters Vitrail's translation-cache key. DH attribute translations must
        // never be served to a packed draw, nor packed translations reused after uninstalling us.
        var defines = new LinkedHashMap<>(callback.getReturnValue());
        defines.put("TERRAVISTA_PACKED_LOD", VitrailPackedPrograms.ABI);
        callback.setReturnValue(defines);
    }
}

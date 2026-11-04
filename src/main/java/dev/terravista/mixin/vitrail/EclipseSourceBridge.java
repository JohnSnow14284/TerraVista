package dev.terravista.mixin.vitrail;

import dev.terravista.compat.EclipsePrograms;
import dev.terravista.compat.VitrailPackedPrograms;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.pack.source.ShaderPackSource", remap = false)
public abstract class EclipseSourceBridge {
    @Shadow @Final private Path shadersRoot;
    @Unique private volatile Boolean terravista$eclipse;
    @Unique private final Map<Path, List<String>> terravista$adapted = new ConcurrentHashMap<>();

    @Inject(method = "readLines", at = @At("RETURN"), cancellable = true)
    private void terravista$eclipse(Path file, CallbackInfoReturnable<List<String>> callback) {
        if (!VitrailPackedPrograms.ENABLED) return;
        if (terravista$eclipse == null) terravista$eclipse = EclipsePrograms.matches(shadersRoot);
        if (terravista$eclipse) callback.setReturnValue(terravista$adapted.computeIfAbsent(file,
                key -> EclipsePrograms.adapt(shadersRoot, key, callback.getReturnValue())));
    }
}

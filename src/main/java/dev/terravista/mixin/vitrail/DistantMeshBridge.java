package dev.terravista.mixin.vitrail;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.terravista.compat.VitrailPackedPrograms;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.render.DistantMesh", remap = false)
public abstract class DistantMeshBridge {
    @Unique private static final VertexFormat terravista$pulling = VertexFormat.builder(0).build();

    @Inject(method = "format", at = @At("HEAD"), cancellable = true)
    private static void terravista$format(List<String> carried, CallbackInfoReturnable<VertexFormat> callback) {
        if (VitrailPackedPrograms.ENABLED) callback.setReturnValue(terravista$pulling);
    }
}

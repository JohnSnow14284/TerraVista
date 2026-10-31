package dev.terravista.mixin.vitrail;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.terravista.compat.VitrailPackedPrograms;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.render.DistantMesh", remap = false)
public abstract class DistantMeshBridge {
    @Inject(method = "format", at = @At("HEAD"), cancellable = true)
    private static void terravista$format(List<String> carried, CallbackInfoReturnable<VertexFormat> callback) {
        // GeometryProgram omits the binding for null. An empty format still declares slot zero
        // and the host frontend requires a vertex buffer even though the shader pulls from SSBOs.
        if (VitrailPackedPrograms.ENABLED) callback.setReturnValue(null);
    }
}

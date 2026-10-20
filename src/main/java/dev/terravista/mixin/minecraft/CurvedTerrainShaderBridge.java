package dev.terravista.mixin.minecraft;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.terravista.render.WorldCurvature;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在资源编译时连接地形曲率变换。 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$Configs")
public class CurvedTerrainShaderBridge {
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void terravista$curvature(Identifier id, ShaderType type, CallbackInfoReturnable<String> result) {
        result.setReturnValue(WorldCurvature.shader(id, type, result.getReturnValue()));
    }
}

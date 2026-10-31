package dev.terravista.mixin.vitrail;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.terravista.compat.VitrailFragments;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.vitrail.render.GeometryProgram", remap = false)
public abstract class NearDepthSamplerBridge {
    @Inject(method = "depth", at = @At("HEAD"), cancellable = true)
    private void terravista$depth(String name, CallbackInfoReturnable<GpuTextureView> callback) {
        if (!VitrailFragments.DEPTH.equals(name)) return;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (target != null) callback.setReturnValue(target.getDepthTextureView());
    }

    @Inject(method = "readsATexture", at = @At("HEAD"), cancellable = true)
    private void terravista$provided(String name, CallbackInfoReturnable<Boolean> callback) {
        if (VitrailFragments.DEPTH.equals(name)
                && Minecraft.getInstance().gameRenderer.mainRenderTarget() != null) callback.setReturnValue(true);
    }
}

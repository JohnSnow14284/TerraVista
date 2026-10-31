package dev.terravista.mixin.vitrail;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.terravista.compat.PassViewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the viewport method added after Vitrail's 26.3 host API baseline. */
@Pseudo
@Mixin(targets = "dev.vitrail.render.LevelPass", remap = false)
public abstract class LevelPassViewportBridge {
    @Shadow private RenderPass real;
    @Shadow private RenderPass real() { throw new AssertionError(); }
    @Unique private PassViewport terravista$viewport;

    // If a later Vitrail implements this method itself, its implementation takes precedence.
    @Unique(silent = true)
    public void setViewport(int x, int y, int width, int height) {
        var pass = real();
        if (terravista$viewport == null) terravista$viewport = new PassViewport();
        terravista$viewport.set(pass, x, y, width, height);
    }

    @Inject(method = "real", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            shift = At.Shift.AFTER))
    private void terravista$restoreViewport(CallbackInfoReturnable<RenderPass> callback) {
        if (terravista$viewport != null) terravista$viewport.restore(real);
    }
}

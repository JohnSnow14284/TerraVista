package dev.terravista.mixin.minecraft;

import dev.terravista.compat.RenderSessionAccess;
import dev.terravista.render.RenderActivation;
import dev.terravista.render.RenderSession;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将世界渲染器的生命周期调用转交会话服务。 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderBridge implements RenderSessionAccess {
    @Unique private final RenderActivation terravista$activation = new RenderActivation();

    @Override public RenderSession terravista$session() { return terravista$activation.current(); }
    @Override public void terravista$close() { terravista$activation.close(); }
    @Override public void terravista$create() { terravista$activation.create(); }
    @Override public void terravista$level(Level level) { terravista$activation.level(level); }

    @Inject(method = "close", at = @At("HEAD"))
    private void terravista$closing(CallbackInfo callback) {
        terravista$close();
    }
}

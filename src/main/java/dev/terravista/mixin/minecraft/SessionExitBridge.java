package dev.terravista.mixin.minecraft;

import dev.terravista.bootstrap.ClientLifecycleHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在客户端断开流程结束后连接会话关闭。 */
@Mixin(Minecraft.class)
public abstract class SessionExitBridge {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void terravista$disconnect(CallbackInfo callback) {
        ClientLifecycleHooks.disconnected();
    }
}

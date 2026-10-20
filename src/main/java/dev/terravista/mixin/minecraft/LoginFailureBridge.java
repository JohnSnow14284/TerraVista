package dev.terravista.mixin.minecraft;

import dev.terravista.bootstrap.StartupFailurePolicy;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在登录错误入口取消断线处理并转交异常。 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class LoginFailureBridge {
    @Inject(method = "onPacketError", at = @At("HEAD"), cancellable = true)
    private void terravista$loginFailure(Packet<?> packet, Exception failure, CallbackInfo ci) {
        if (StartupFailurePolicy.isLogin(packet)) {
            ci.cancel();
            StartupFailurePolicy.failLogin(failure);
        }
    }
}

package dev.terravista.mixin.minecraft;

import dev.terravista.bootstrap.ClientLifecycleHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在登录世界信息读取前连接会话启动。 */
@Mixin(ClientPacketListener.class)
public abstract class SessionLoginBridge {
    @Inject(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;commonPlayerSpawnInfo()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;"))
    private void terravista$login(ClientboundLoginPacket packet, CallbackInfo callback) {
        ClientLifecycleHooks.loggedIn();
    }
}

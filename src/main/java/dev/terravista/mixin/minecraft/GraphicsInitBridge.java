package dev.terravista.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.terravista.bootstrap.ClientBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在图形初始化返回时连接客户端设备与会话工厂。 */
@Mixin(RenderSystem.class)
public abstract class GraphicsInitBridge {
    @Inject(method = "initRenderer", order = 900, remap = false, at = @At("RETURN"))
    private static void terravista$graphics(GpuDevice device, CallbackInfo callback) {
        ClientBootstrap.initialize(device);
    }
}

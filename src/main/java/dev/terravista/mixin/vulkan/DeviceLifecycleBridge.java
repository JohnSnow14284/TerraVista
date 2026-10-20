package dev.terravista.mixin.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import dev.terravista.render.gpu.DeviceAttachment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将宿主设备构造和关闭边界转交适配服务。 */
@Mixin(VulkanDevice.class)
public abstract class DeviceLifecycleBridge {
    @Shadow @Final private FeatureSet enabledFeatures;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terravista$attach(CallbackInfo callback) {
        DeviceAttachment.attach((VulkanDevice) (Object) this, enabledFeatures);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void terravista$detach(CallbackInfo callback) {
        DeviceAttachment.detach();
    }
}

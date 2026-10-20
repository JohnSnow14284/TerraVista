package dev.terravista.mixin.minecraft;

import dev.terravista.render.DistantScene;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在视锥和投影创建前扩展远处对象的裁剪距离。 */
@Mixin(Camera.class)
public abstract class DistantCameraBridge {
    @Shadow private float depthFar;
    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = 181, shift = At.Shift.AFTER))
    private void terravista$farPlane(CallbackInfo callback) { depthFar = DistantScene.farPlane(depthFar); }
}

package dev.terravista.mixin.vitrail;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.terravista.compat.VitrailBuffers;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends Vitrail's existing native SSBO descriptor mechanism for the packed LOD buffers. */
@Pseudo
@Mixin(targets = "dev.vitrail.render.storage.StorageBuffers", remap = false)
public abstract class StorageBuffersBridge {
    @Inject(method = "named", at = @At("HEAD"), cancellable = true)
    private static void terravista$named(String name, CallbackInfoReturnable<Boolean> callback) {
        if (VitrailBuffers.named(name)) callback.setReturnValue(true);
    }

    @Inject(method = "bound", at = @At("HEAD"), cancellable = true)
    private static void terravista$bound(String name, CallbackInfoReturnable<Object> callback) {
        if (VitrailBuffers.named(name)) callback.setReturnValue(VitrailBuffers.bound(name));
    }

    @Inject(method = "bind", at = @At("HEAD"))
    private static void terravista$bind(RenderPass pass, List<String> names, CallbackInfo callback) {
        VitrailBuffers.bind(pass, names);
    }

    @WrapOperation(method = "bind", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
    private static void terravista$keepNativeSlice(RenderPass pass, String name, GpuBufferSlice slice,
                                                  Operation<Void> original) {
        if (!VitrailBuffers.named(name)) original.call(pass, name, slice);
    }
}

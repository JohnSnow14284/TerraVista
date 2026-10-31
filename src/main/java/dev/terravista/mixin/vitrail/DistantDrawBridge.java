package dev.terravista.mixin.vitrail;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.terravista.compat.VitrailLods;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "dev.vitrail.render.DistantDraw", remap = false)
public abstract class DistantDrawBridge {
    @WrapOperation(method = "record", at = @At(value = "INVOKE", target =
            "Ldev/vitrail/render/DistantDraw$Corners;write(Lcom/mojang/renderpearl/api/device/GpuDevice;Ljava/util/List;Lnet/minecraft/world/phys/Vec3;)I"))
    private int terravista$corners(@Coerce Object corners, GpuDevice device, List<?> sections,
                                   Vec3 camera, Operation<Integer> original) {
        return VitrailLods.recording() ? 0 : original.call(corners, device, sections, camera);
    }

    @WrapOperation(method = "record", at = @At(value = "INVOKE", target =
            "Ldev/vitrail/render/DistantProgram;bind(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void terravista$draw(@Coerce Object program, RenderPass pass, Operation<Void> original) {
        original.call(program, pass);
        VitrailLods.emit(pass);
    }
}

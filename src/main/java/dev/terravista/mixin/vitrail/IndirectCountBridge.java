package dev.terravista.mixin.vitrail;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import dev.terravista.compat.VitrailIndirect;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VulkanRenderPass.class, remap = false)
public abstract class IndirectCountBridge {
    @WrapOperation(method = "drawIndexedIndirect", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndexedIndirect(Lorg/lwjgl/vulkan/VkCommandBuffer;JJII)V"))
    private void terravista$count(VkCommandBuffer command, long buffer, long offset, int count,
                                   int stride, Operation<Void> original) {
        if (!VitrailIndirect.counted(command, buffer, offset, count, stride)) {
            original.call(command, buffer, offset, count, stride);
        }
    }
}

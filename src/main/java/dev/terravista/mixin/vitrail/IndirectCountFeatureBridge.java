package dev.terravista.mixin.vitrail;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import dev.terravista.compat.VitrailPackedPrograms;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ask the host to enable GPU draw counts only on devices that support the optional feature. */
@Mixin(value = VulkanFeatureSets.class, remap = false)
public abstract class IndirectCountFeatureBridge {
    @Inject(method = "optionalFeatureSets", at = @At("RETURN"), cancellable = true)
    private static void terravista$counts(CallbackInfoReturnable<Set<FeatureSet>> callback) {
        if (!VitrailPackedPrograms.ENABLED) return;
        var optional = new HashSet<>(callback.getReturnValue());
        optional.add(new FeatureSet("TerraVista GPU draw counts", Set.of(), Set.of(
                new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "drawIndirectCount"))));
        callback.setReturnValue(optional);
    }
}

package dev.terravista.mixin.sodium;

import dev.terravista.compat.SodiumVisibility;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 转交 Sodium 网格上传的淡入起点参数。 */
@Mixin(value = RenderRegionManager.class, remap = false)
public class TerrainFadeBridge {
    @ModifyArg(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager$PendingSectionMeshUpload;<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;ILnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionMeshParts;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/gpu/arena/PendingUpload;)V"), remap = false, index = 1)
    private int terravista$fadeOrigin(int original) {
        return SodiumVisibility.fadeOrigin(original);
    }
}

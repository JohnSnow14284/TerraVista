package dev.terravista.mixin.sodium;

import dev.terravista.bootstrap.RuntimeEnvironment;
import dev.terravista.compat.SodiumSections;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 转交区块管理器的可见性收集和采集事件。 */
@Mixin(value = RenderSectionManager.class, remap = false)
public class SectionManagerBridge {
    @Unique private static final boolean terravista$bobby = RuntimeEnvironment.modLoaded("bobby");
    @Unique private final SodiumSections terravista$sections = new SodiumSections();
    @Shadow @Final private ClientLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terravista$sectionBase(ClientLevel level, int renderDistance, SortBehavior sorting, CallbackInfo ci) {
        terravista$sections.initialize(this.level);
    }

    @Inject(method = "renderOutOfGraph", at = @At("HEAD"))
    private void terravista$fallbackVisibility(Viewport viewport, FogParameters fog, CallbackInfo ci) {
        SodiumSections.beginVisibility();
    }

    @Inject(method = "readRenderListFromTree", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector;<init>(Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;I)V"))
    private void terravista$treeVisibility(Viewport viewport, FogParameters fog, CallbackInfo ci) {
        SodiumSections.beginVisibility();
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void terravista$removedChunk(int x, int z, CallbackInfo ci) {
        SodiumSections.removed(level, x, z, terravista$bobby);
    }

    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void terravista$addedChunk(int x, int z, CallbackInfo ci) {
        SodiumSections.added(level, x, z);
    }

    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)I"))
    private int terravista$sectionUploaded(RenderSection section, BuiltSectionInfo info) {
        return terravista$sections.uploaded(level, section, info);
    }
}

package dev.terravista.mixin.sodium;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.terravista.compat.SodiumFrameEvents;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 转交 Sodium 地形绘制前的帧参数。 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumDrawBridge {
    @Inject(method = "drawChunkLayer", at = @At("HEAD"))
    private void terravista$terrainFrame(RenderPass pass, ChunkSectionLayerGroup group,
            ChunkRenderMatrices matrices, double x, double y, double z,
            GpuSampler sampler, OitStage stage, CallbackInfo ci) {
        SodiumFrameEvents.capture(group, matrices, x, y, z);
    }
}

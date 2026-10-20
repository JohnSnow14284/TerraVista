package dev.terravista.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.terravista.render.TerrainPassBridge;
import dev.terravista.render.TerrainPasses;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 连接原版地形通道关闭和透明合成位置。 */
@Mixin(LevelRenderer.class)
public abstract class TerrainPassBridgeMixin {
    @WrapMethod(method = "executeSolid")
    private void terravista$solid(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame features,
            RenderPass terrain, Operation<Void> original) {
        TerrainPasses.solid(sections, features, terrain, original);
    }

    @ModifyVariable(method = "executeSolid", argsOnly = true, ordinal = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 1, shift = At.Shift.AFTER))
    private RenderPass terravista$features(RenderPass terrain) {
        return TerrainPasses.beforeFeatures(terrain);
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void terravista$classicTransparency(LevelRenderer renderer, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame features, RenderPass solid, Operation<Void> original) {
        TerrainPasses.classicTransparency(renderer, sections, features, solid, original);
    }

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;close()V", ordinal = 0, shift = At.Shift.AFTER))
    private void terravista$terrainClosed(GpuBufferSlice fog, boolean useOit, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame features, boolean seeThrough, boolean alwaysOnTop, CallbackInfo callback) {
        TerrainPasses.terrainClosed(useOit);
    }

    @Inject(method = "executeOit", at = @At("TAIL"))
    private void terravista$oitCompleted(CallbackInfo callback) {
        TerrainPassBridge.finishTranslucent();
    }
}

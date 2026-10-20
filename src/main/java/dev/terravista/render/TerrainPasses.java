package dev.terravista.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import java.util.Optional;
import java.util.OptionalDouble;

/** 在原版地形与透明通道之间安排远景提交和帧清理。 */
public final class TerrainPasses {
    private static RenderPass featuresPass;

    public static void solid(ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame features, RenderPass terrain, Operation<Void> original) {
        try {
            original.call(sections, features, terrain);
        } finally {
            if (featuresPass != null) {
                var pass = featuresPass;
                featuresPass = null;
                pass.close();
            }
        }
    }

    /** 地形深度合成完成后再绘制实体和特效。 */
    public static RenderPass beforeFeatures(RenderPass terrain) {
        if (!TerrainPassBridge.requiresOwnPass()) return terrain;
        terrain.close();
        TerrainPassBridge.submitOpaque();
        featuresPass = mainPass("Terrain features after TerraVista");
        RenderSystem.bindDefaultUniforms(featuresPass);
        return featuresPass;
    }

    public static void classicTransparency(LevelRenderer renderer, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame features, RenderPass solid, Operation<Void> original) {
        if (!TerrainPassBridge.requiresOwnPass()) {
            original.call(renderer, sections, features, solid);
            return;
        }
        solid.close();
        TerrainPassBridge.submitOpaque();
        try (RenderPass water = mainPass("Terrain classic transparency after TerraVista")) {
            RenderSystem.bindDefaultUniforms(water);
            original.call(renderer, sections, features, water);
        }
    }

    public static void terrainClosed(boolean useOit) {
        TerrainPassBridge.submitOpaque();
        if (!useOit) TerrainPassBridge.finishTranslucent();
    }

    private static RenderPass mainPass(String name) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> name, target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty());
    }

    private TerrainPasses() {}
}

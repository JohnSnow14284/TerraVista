package dev.terravista.compat;

import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.render.TerrainPassBridge;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;

/** 连接 Sodium 地形帧采集与构建线程预算更新。 */
public final class SodiumFrameEvents {
    public static void capture(ChunkSectionLayerGroup group, ChunkRenderMatrices matrices,
                               double x, double y, double z) {
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            TerrainPassBridge.capture(matrices, x, y, z);
        }
    }

    public static void initialized() {
        var session = SessionLifecycle.current();
        if (session != null) SodiumWorkers.update(session);
    }

    private SodiumFrameEvents() {}
}

package dev.terravista.render;

import dev.terravista.compat.RenderSessionAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

/** 连接场景提取器的世界切换和渲染重建。 */
public final class ExtractionLifecycle {
    public static void level(LevelRenderer renderer, ClientLevel level) {
        ((RenderSessionAccess)renderer).terravista$level(level);
    }

    public static void rebuild(LevelRenderer renderer) {
        ((RenderSessionAccess)renderer).terravista$close();
        ((RenderSessionAccess)renderer).terravista$create();
    }

    private ExtractionLifecycle() {}
}

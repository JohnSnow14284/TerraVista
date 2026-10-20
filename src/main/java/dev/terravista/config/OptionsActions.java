package dev.terravista.config;

import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.compat.RenderSessionAccess;
import dev.terravista.compat.SodiumWorkers;
import net.minecraft.client.Minecraft;

/** 将距离和线程配置变更应用到当前会话。 */
public final class OptionsActions {
    private final ClientOptions options;

    public OptionsActions(ClientOptions options) { this.options = options; }

    public void distanceApplied(Integer value) {
        var renderer = RenderSessionAccess.current();
        if (renderer != null) renderer.distance(options.renderDistanceChunks(
                Minecraft.getInstance().options.getEffectiveRenderDistance()) / 32f);
    }

    public static void threadsApplied() {
        var session = SessionLifecycle.current();
        if (session != null) SodiumWorkers.update(session);
    }
}

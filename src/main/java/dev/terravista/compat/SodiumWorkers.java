package dev.terravista.compat;

import dev.terravista.config.ClientOptions;
import dev.terravista.mixin.sodium.SodiumRendererAccess;
import dev.terravista.world.WorldSession;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

/** 根据客户端配置与 Sodium 构建线程数调整专用线程池。 */
public final class SodiumWorkers {
    private SodiumWorkers() {}

    public static void update(WorldSession session) {
        int target = ClientOptions.CONFIG.serviceThreads;
        var renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer != null) {
            var sections = ((SodiumRendererAccess) renderer).terravista$sections();
            if (sections != null) {
                session.dedicatedThreads(Math.max(1, target - sections.getBuilder().getTotalThreadCount()));
                return;
            }
        }
        session.dedicatedThreads(target);
    }
}

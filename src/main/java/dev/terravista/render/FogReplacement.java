package dev.terravista.render;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.compat.RenderSessionAccess;
import dev.terravista.config.ClientOptions;
import net.minecraft.client.renderer.fog.FogData;

/** 根据当前渲染状态替换地形雾并保留天空和云层范围。 */
public final class FogReplacement {
    private static boolean announced;

    public static boolean active() {
        return ClientOptions.CONFIG.renderingEnabled(SessionLifecycle::available, GraphicsMode::nativeVulkan)
                && RenderSessionAccess.current() != null;
    }

    public static void replace(FogData data) {
        if (!announced) {
            announced = true;
            ModLog.info("Render: TerraVista replacing terrain distance fog; preserving Minecraft sky and cloud fog.");
        }
        var ranges = FogOwnership.apply(new FogOwnership.Ranges(
                data.environmentalStart, data.environmentalEnd, data.renderDistanceStart,
                data.renderDistanceEnd, data.skyEnd, data.cloudEnd), true,
                ClientOptions.CONFIG.fog().removesVanillaEnvFog);
        if (ClientOptions.CONFIG.fog() == ClientOptions.FogMode.CUSTOM) {
            ranges = FogOwnership.custom(ranges, ClientOptions.CONFIG.fogStrength, ClientOptions.CONFIG.fogReachChunks);
        }
        data.environmentalStart = ranges.environmentStart();
        data.environmentalEnd = ranges.environmentEnd();
        data.renderDistanceStart = ranges.distanceStart();
        data.renderDistanceEnd = ranges.distanceEnd();
    }

    private FogReplacement() {}
}

package dev.terravista.config;

import dev.terravista.bootstrap.CommonEntrypoint;
import dev.terravista.bootstrap.SessionLifecycle;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.resources.Identifier;
import java.util.Objects;

/** 在客户端服务可用时向Sodium注册模组配置页面。 */
@ConfigEntryPointForge("terravista")
public final class OptionsEntryPoint implements ConfigEntryPoint {
    @Override public void registerConfigLate(ConfigBuilder builder) {
        if (!SessionLifecycle.available()) return;
        var config = ClientOptions.CONFIG;
        var options = builder.registerModOptions("terravista", "TerraVista", CommonEntrypoint.version())
                .setIcon(Identifier.parse("terravista:ui/tv.png"));
        Objects.requireNonNull(config);
        OptionsPages.register(builder, options, config);
    }
}

package dev.terravista.bootstrap;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

/** 连接公共启动初始化。 */
@Mod("terravista")
public final class CommonEntrypoint {
    private static String version;

    public CommonEntrypoint(ModContainer container) {
        version = container.getModInfo().getVersion().toString();
        CommonRegistrations.register(container);
    }
    public static String version() { return version; }
}

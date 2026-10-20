package dev.terravista.bootstrap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** 连接客户端初始化注册。 */
@Mod(value = "terravista", dist = Dist.CLIENT)
public final class ClientEntrypoint {
    public ClientEntrypoint(ModContainer container) {
        ClientRegistrations.register(container);
    }
}

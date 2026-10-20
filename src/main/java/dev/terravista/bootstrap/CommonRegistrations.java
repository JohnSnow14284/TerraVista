package dev.terravista.bootstrap;

import dev.terravista.world.PlayerLocationBroadcast;
import dev.terravista.world.PlayerSightings;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 集中注册可选网络协议与服务端事件。 */
public final class CommonRegistrations {
    private CommonRegistrations() {}
    public static void register(ModContainer container) {
        container.getEventBus().addListener(CommonRegistrations::payloads);
        NeoForge.EVENT_BUS.addListener(PlayerLocationBroadcast::tick);
    }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(PlayerSightings.TYPE, PlayerSightings.CODEC);
    }
}

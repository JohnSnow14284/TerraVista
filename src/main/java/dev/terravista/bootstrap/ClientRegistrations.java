package dev.terravista.bootstrap;

import dev.terravista.compat.DebugRegistration;
import dev.terravista.config.ConfigScreenFactory;
import dev.terravista.render.DistantPlayers;
import dev.terravista.render.DistantBeacons;
import dev.terravista.render.DistantScene;
import dev.terravista.world.PlayerSightings;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** 集中连接客户端界面、调试和按键注册。 */
public final class ClientRegistrations {
    private ClientRegistrations() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> ConfigScreenFactory.create(parent));
        var events = container.getEventBus();
        events.addListener(DebugRegistration::register);
        events.addListener(ClientHotkeys::register);
        events.addListener(ClientRegistrations::payloads);
        NeoForge.EVENT_BUS.addListener(ClientHotkeys::tick);
        NeoForge.EVENT_BUS.addListener(DistantScene::tick);
        NeoForge.EVENT_BUS.addListener(DistantScene::extract);
        NeoForge.EVENT_BUS.addListener(DistantBeacons::chunkLoaded);
    }
    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(PlayerSightings.TYPE, (payload, context) -> DistantPlayers.receive(payload));
    }
}

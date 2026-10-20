package dev.terravista.world;

import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 为已协商协议的客户端发送同维度可见玩家快照。 */
public final class PlayerLocationBroadcast {
    private PlayerLocationBroadcast() {}
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        double range = 131072.0;
        var players = server.getPlayerList().getPlayers();
        var snapshots = new HashMap<ServerPlayer, PlayerSightings.Player>();
        for (ServerPlayer viewer : players) {
            if (!viewer.connection.hasChannel(PlayerSightings.TYPE)) continue;
            var entries = new ArrayList<PlayerSightings.Player>();
            for (ServerPlayer player : players) {
                if (player == viewer || player.level() != viewer.level() || player.isSpectator()
                        || player.isInvisibleTo(viewer) || player.distanceToSqr(viewer) > range * range) continue;
                entries.add(snapshots.computeIfAbsent(player, source -> new PlayerSightings.Player(player.getUUID(), player.getGameProfile().name(),
                        player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.yHeadRot,
                        player.getPose(), player.isFallFlying(), PlayerSightings.SLOTS.stream()
                                .map(slot -> player.getItemBySlot(slot).copy()).toList())));
            }
            int parts = Math.max(1, (entries.size() + PlayerSightings.BATCH_SIZE - 1) / PlayerSightings.BATCH_SIZE);
            for (int part = 0; part < parts; part++) {
                int from = part * PlayerSightings.BATCH_SIZE, to = Math.min(entries.size(), from + PlayerSightings.BATCH_SIZE);
                PacketDistributor.sendToPlayer(viewer, new PlayerSightings(viewer.level().dimension().identifier(),
                        server.getTickCount(), part, parts, entries.subList(from, to)));
            }
        }
    }
}

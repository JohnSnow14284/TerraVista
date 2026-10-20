package dev.terravista.render;

import com.mojang.authlib.GameProfile;
import dev.terravista.world.PlayerSightings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 接收完整玩家快照并维护不加入世界的插值模型。 */
public final class DistantPlayers {
    private static final Map<UUID, Proxy> players = new HashMap<>();
    private static final Map<UUID, PlayerSightings.Player> incoming = new HashMap<>();
    private static ClientLevel level;
    private static long frame = -1, receivedAt;
    private static int nextPart, expectedParts, nextId = -1;
    private DistantPlayers() {}

    public static void receive(PlayerSightings snapshot) {
        var current = Minecraft.getInstance().level;
        if (current == null || !current.dimension().identifier().equals(snapshot.dimension())) return;
        if (level != current) { clear(); level = current; }
        if (snapshot.part() == 0) {
            if (snapshot.frame() <= frame) return;
            incoming.clear(); frame = snapshot.frame(); nextPart = 0; expectedParts = snapshot.parts();
        }
        if (snapshot.frame() != frame || snapshot.part() != nextPart || snapshot.parts() != expectedParts) return;
        for (var player : snapshot.players()) incoming.put(player.id(), player);
        if (++nextPart != expectedParts) return;
        players.keySet().retainAll(incoming.keySet());
        for (var entry : incoming.values()) {
            var proxy = players.computeIfAbsent(entry.id(), id -> new Proxy(current, entry));
            proxy.accept(entry);
        }
        incoming.clear(); receivedAt = System.nanoTime();
    }
    public static void tick() {
        if (level != Minecraft.getInstance().level || (receivedAt != 0 && System.nanoTime() - receivedAt > 3_000_000_000L)) {
            clear(); return;
        }
        for (var player : players.values()) player.advance();
    }
    static Iterable<Proxy> models() { return players.values(); }
    public static void clear() {
        players.clear(); incoming.clear(); level = null; frame = -1; nextPart = 0; receivedAt = 0;
    }

    static final class Proxy extends RemotePlayer {
        private Vec3 target;
        private float targetYaw, targetPitch, targetHead;
        private int remaining;
        Proxy(ClientLevel level, PlayerSightings.Player player) {
            super(level, new GameProfile(player.id(), player.name()));
            setId(nextId--);
            setPos(player.x(), player.y(), player.z());
            xOld = xo = getX(); yOld = yo = getY(); zOld = zo = getZ();
            setYRot(player.yaw()); setXRot(player.pitch());
            yHeadRot = yHeadRotO = player.head(); yBodyRot = yBodyRotO = player.yaw();
        }
        void accept(PlayerSightings.Player player) {
            target = new Vec3(player.x(), player.y(), player.z());
            targetYaw = player.yaw(); targetPitch = player.pitch(); targetHead = player.head(); remaining = 10;
            setPose(player.pose()); setSharedFlag(7, player.flying());
            for (int i = 0; i < PlayerSightings.SLOTS.size(); i++) setItemSlot(PlayerSightings.SLOTS.get(i), player.equipment().get(i));
            if (position().distanceToSqr(target) > 64 * 64) {
                setPos(target); xOld = xo = getX(); yOld = yo = getY(); zOld = zo = getZ();
            }
        }
        void advance() {
            xOld = xo = getX(); yOld = yo = getY(); zOld = zo = getZ();
            yRotO = getYRot(); xRotO = getXRot(); yHeadRotO = yHeadRot; yBodyRotO = yBodyRot;
            if (remaining > 0) {
                float step = 1f / remaining--;
                setPos(position().lerp(target, step));
                setYRot(Mth.rotLerp(step, getYRot(), targetYaw)); setXRot(Mth.lerp(step, getXRot(), targetPitch));
                yHeadRot = Mth.rotLerp(step, yHeadRot, targetHead); yBodyRot = getYRot();
            }
            tickCount++;
            calculateEntityAnimation(false);
        }
    }
}

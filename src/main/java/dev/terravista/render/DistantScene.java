package dev.terravista.render;

import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.config.ClientOptions;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

/** 将远处玩家与已知信标接入游戏场景提取。 */
public final class DistantScene {
    private DistantScene() {}
    public static boolean enabled() { return GraphicsMode.nativeVulkan() && SessionLifecycle.current() != null; }
    public static float farPlane(float original) {
        if (!enabled()) return original;
        return Math.max(original, ClientOptions.CONFIG.renderDistanceChunks(Minecraft.getInstance().options.getEffectiveRenderDistance()) * 16f + 2048f);
    }
    public static void tick(ClientTickEvent.Post event) {
        DistantBeacons.bind(enabled() ? Minecraft.getInstance().level : null);
        DistantPlayers.tick();
    }
    public static void extract(ExtractLevelRenderStateEvent event) {
        if (!enabled()) return;
        var client = Minecraft.getInstance();
        var output = event.getRenderState();
        Vec3 camera = event.getCamera().position();
        double radius = ClientOptions.CONFIG.renderDistanceChunks(client.options.getEffectiveRenderDistance()) * 16.0;
        {
            var rendered = new HashSet<Integer>();
            for (var state : output.entityRenderStates) if (state instanceof AvatarRenderState avatar) rendered.add(avatar.id);
            for (var player : event.getLevel().players()) appendPlayer(event, player, rendered, camera, radius);
            for (var proxy : DistantPlayers.models()) {
                if (event.getLevel().getPlayerByUUID(proxy.getUUID()) == null)
                    appendPlayer(event, proxy, rendered, camera, radius);
            }
            output.lastEntityRenderStateCount = output.entityRenderStates.size();
        }
        {
            var rendered = new HashSet<BlockPos>();
            for (var state : output.blockEntityRenderStates) if (state instanceof BeaconRenderState) rendered.add(state.blockPos);
            for (var beam : DistantBeacons.beams()) {
                BlockPos position = BlockPos.of(beam.position());
                double dx = position.getX() + 0.5 - camera.x, dz = position.getZ() + 0.5 - camera.z;
                double distanceSquared = dx * dx + dz * dz;
                if (rendered.contains(position) || distanceSquared > radius * radius) continue;
                float scale = beamRadiusScale(Math.sqrt(distanceSquared), client.options.getEffectiveRenderDistance() * 16.0,
                        client.player.isScoping());
                double extent = Math.max(0.5, scale * 0.3);
                long height = 2048;
                for (int index = 0; index + 1 < beam.sections().size(); index++) height += beam.sections().get(index).height();
                if (!event.getFrustum().isVisible(new AABB(position.getX() + 0.5 - extent, position.getY(), position.getZ() + 0.5 - extent,
                        position.getX() + 0.5 + extent, position.getY() + height, position.getZ() + 0.5 + extent))) continue;
                var state = new BeaconRenderState();
                state.blockPos = position; state.blockEntityType = BlockEntityTypes.BEACON; state.lightCoords = 15728880;
                state.animationTime = Math.floorMod(event.getLevel().getGameTime(), 40) + output.worldPartialTicks;
                state.beamRadiusScale = scale;
                state.sections = beam.sections().stream().map(segment -> new BeaconRenderState.Section(segment.color(), segment.height())).toList();
                output.blockEntityRenderStates.add(state);
            }
        }
    }

    /** 延续原版宽度，在交接范围外平滑放缓增宽。 */
    static float beamRadiusScale(double distance, double nativeRange, boolean scoped) {
        if (scoped) return 1;
        double excess = Math.max(0, distance - nativeRange);
        double eased = excess * excess / (excess + 512);
        return (float) (Math.max(1, Math.min(distance, nativeRange) / 96) + eased * 0.0011);
    }
    private static void appendPlayer(ExtractLevelRenderStateEvent event, AbstractClientPlayer player,
                                     HashSet<Integer> rendered, Vec3 camera, double radius) {
        if (player == event.getCamera().entity() || rendered.contains(player.getId()) || player.isSpectator()
                || player.isInvisibleTo(Minecraft.getInstance().player) || player.position().distanceToSqr(camera) > radius * radius
                || !event.getFrustum().isVisible(player.getBoundingBox().inflate(0.5))) return;
        var renderer = Minecraft.getInstance().levelRenderer.entityRenderDispatcher();
        var state = renderer.extractEntity(player, event.getRenderState().worldPartialTicks);
        state.shadowPieces.clear(); state.shadowRadius = 0;
        event.getRenderState().entityRenderStates.add(state);
    }
}

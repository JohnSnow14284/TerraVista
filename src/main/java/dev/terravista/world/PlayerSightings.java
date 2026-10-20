package dev.terravista.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/** 编解码按维度分批发送的玩家外观与位置快照。 */
public record PlayerSightings(Identifier dimension, long frame, int part, int parts, List<Player> players) implements CustomPacketPayload {
    public static final Type<PlayerSightings> TYPE = new Type<>(Identifier.parse("terravista:player_sightings"));
    public static final int BATCH_SIZE = 32;
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    public record Player(UUID id, String name, double x, double y, double z, float yaw, float pitch, float head,
                         Pose pose, boolean flying, List<ItemStack> equipment) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSightings> CODEC = new StreamCodec<>() {
        @Override public PlayerSightings decode(RegistryFriendlyByteBuf buffer) {
            var dimension = buffer.readIdentifier();
            long frame = buffer.readLong();
            int part = buffer.readVarInt(), parts = buffer.readVarInt(), count = buffer.readVarInt();
            if (parts < 1 || parts > 1024 || part < 0 || part >= parts || count < 0 || count > BATCH_SIZE)
                throw new IllegalArgumentException("TerraVista Network: Invalid player snapshot batch");
            var players = new ArrayList<Player>(count);
            for (int i = 0; i < count; i++) {
                UUID id = buffer.readUUID(); String name = buffer.readUtf(64);
                double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
                float yaw = buffer.readFloat(), pitch = buffer.readFloat(), head = buffer.readFloat();
                int poseIndex = buffer.readVarInt();
                if (poseIndex < 0 || poseIndex >= Pose.values().length) throw new IllegalArgumentException("TerraVista Network: Invalid player pose");
                Pose pose = Pose.values()[poseIndex]; boolean flying = buffer.readBoolean();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(head))
                    throw new IllegalArgumentException("TerraVista Network: Non-finite player location");
                var equipment = new ArrayList<ItemStack>(SLOTS.size());
                for (var ignored : SLOTS) equipment.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                players.add(new Player(id, name, x, y, z, yaw, pitch, head, pose, flying, List.copyOf(equipment)));
            }
            return new PlayerSightings(dimension, frame, part, parts, List.copyOf(players));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PlayerSightings value) {
            buffer.writeIdentifier(value.dimension); buffer.writeLong(value.frame);
            buffer.writeVarInt(value.part); buffer.writeVarInt(value.parts); buffer.writeVarInt(value.players.size());
            for (var player : value.players) {
                buffer.writeUUID(player.id); buffer.writeUtf(player.name, 64);
                buffer.writeDouble(player.x); buffer.writeDouble(player.y); buffer.writeDouble(player.z);
                buffer.writeFloat(player.yaw); buffer.writeFloat(player.pitch); buffer.writeFloat(player.head);
                buffer.writeVarInt(player.pose.ordinal()); buffer.writeBoolean(player.flying);
                for (var stack : player.equipment) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            }
        }
    };
    @Override public Type<PlayerSightings> type() { return TYPE; }
}

package dev.terravista.render;

import com.google.gson.Gson;
import dev.terravista.bootstrap.ModLog;
import dev.terravista.config.ClientStorageLocation;
import dev.terravista.storage.WorldStorageIdentity;
import dev.terravista.world.WorldAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** 保存已知信标光束并在方块变化和世界切换时更新缓存。 */
public final class DistantBeacons {
    public record Segment(int color, int height) {}
    public record Beam(long position, List<Segment> sections) {}
    private static final Gson JSON = new Gson();
    private static final Long2ObjectOpenHashMap<Beam> beams = new Long2ObjectOpenHashMap<>();
    private static ClientLevel level;
    private static Path file;
    private static boolean dirty, writable;
    private DistantBeacons() {}

    public static void bind(ClientLevel current) {
        if (level == current) return;
        flush(); beams.clear(); level = current; file = null; dirty = false; writable = true;
        if (current == null) return;
        var identity = WorldAccess.identity(current);
        if (identity == null) return;
        file = ClientStorageLocation.current().resolve(WorldStorageIdentity.directory(identity)).resolve("beacons.json");
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file)) {
            var stored = JSON.fromJson(reader, Beam[].class);
            if (stored == null) throw new IllegalArgumentException("Missing beacon collection");
            for (var beam : stored) {
                if (beam == null || beam.sections == null || beam.sections.isEmpty() || beam.sections.size() > 4096
                        || beam.sections.stream().anyMatch(segment -> segment == null || segment.height < 0))
                    throw new IllegalArgumentException("Invalid beacon record");
                beams.put(beam.position, beam);
            }
        } catch (IOException | RuntimeException exception) {
            beams.clear(); writable = false;
            ModLog.error("Beacons: Could not read cached beams", exception);
        }
    }
    public static void observe(Level source, BlockPos position, BeaconBlockEntity beacon) {
        if (source != Minecraft.getInstance().level || !DistantScene.enabled() || source.getGameTime() % 10 != 0) return;
        if (beacon.isRemoved() || !source.hasChunkAt(position) || source.getBlockEntity(position) != beacon) return;
        bind((ClientLevel) source);
        var sections = beacon.getBeamSections().stream().map(section -> new Segment(section.getColor(), section.getHeight())).toList();
        if (sections.isEmpty()) remove(position);
        else {
            var beam = new Beam(position.asLong(), sections);
            dirty |= !beam.equals(beams.put(beam.position, beam));
        }
    }
    public static void changed(Level source, BlockPos position, BlockState previous, BlockState updated) {
        if (source == level && previous.is(Blocks.BEACON) && !updated.is(Blocks.BEACON)) remove(position);
    }
    private static void remove(BlockPos position) { dirty |= beams.remove(position.asLong()) != null; }
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() != Minecraft.getInstance().level || !DistantScene.enabled()) return;
        bind(Minecraft.getInstance().level);
        var chunk = event.getChunk();
        var position = chunk.getPos();
        dirty |= beams.long2ObjectEntrySet().removeIf(entry -> {
            var block = BlockPos.of(entry.getLongKey());
            return block.getX() >> 4 == position.x() && block.getZ() >> 4 == position.z()
                    && !chunk.getBlockState(block).is(Blocks.BEACON);
        });
    }
    static Iterable<Beam> beams() { return beams.values(); }
    private static void flush() {
        if (!dirty || file == null || !writable) return;
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "beacons-", ".tmp");
            Files.writeString(temporary, JSON.toJson(beams.values().toArray(Beam[]::new)));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (IOException exception) { ModLog.error("Beacons: Could not store cached beams", exception); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException exception) { ModLog.warn("Beacons: Temporary beam file could not be removed", exception); }
        }
    }
}

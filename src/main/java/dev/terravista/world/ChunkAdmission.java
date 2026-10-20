package dev.terravista.world;

import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** 分别按会话规则接收实时采集与原始导入。 */
public final class ChunkAdmission {
    private ChunkAdmission() {}

    public static boolean capture(WorldIdentity identity, LevelChunk chunk) {
        if (identity == null) return false;
        var session = SessionLifecycle.current();
        if (session == null) return false;
        if (!session.ingestEnabled(identity)) return false;
        var world = session.worlds().getOrCreate(identity);
        if (world == null) return false;
        return ChunkCapture.enqueue(session.ingest(), world, chunk);
    }
    public static boolean captureAutomatic(LevelChunk chunk) {
        return capture(WorldAccess.identity(chunk.getLevel()), chunk);
    }
    public static boolean raw(WorldIdentity identity, LevelChunkSection section, int x, int y, int z,
                              DataLayer block, DataLayer sky) {
        if (identity == null) return false;
        var world = WorldAccess.getOrCreate(identity);
        if (world == null) return false;
        return raw(world, section, x, y, z, block, sky);
    }
    public static boolean raw(VoxelWorld world, LevelChunkSection section, int x, int y, int z,
                              DataLayer block, DataLayer sky) {
        var registry = world.registry;
        if (registry == null) return false;
        var session = registry.session();
        if (session == null) return false;
        if (!session.ingestEnabled(null)) return false;
        return session.ingest().enqueue(world, section, x, y, z, block, sky);
    }
}

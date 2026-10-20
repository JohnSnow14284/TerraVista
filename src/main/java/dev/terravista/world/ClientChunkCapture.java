package dev.terravista.world;

import dev.terravista.compat.UnboundedChunkAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** 查询缓存槽位并在兼容区块卸载前提交采集。 */
public final class ClientChunkCapture {
    public static LevelChunk lookup(ClientChunkCache.Storage chunks, ClientChunkCache.Storage indexing, int x, int z) {
        var chunk = chunks.getChunk(indexing.getIndex(x, z));
        if (chunk == null) return null;
        return chunk.getPos().x() == x && chunk.getPos().z() == z ? chunk : null;
    }

    public static void unloading(UnboundedChunkAccess chunks, ChunkPos position, boolean bobby) {
        if (bobby) {
            var chunk = chunks.terravista$chunk(position.x(), position.z());
            if (chunk != null) ChunkAdmission.captureAutomatic(chunk);
        }
    }

    private ClientChunkCapture() {}
}

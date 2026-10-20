package dev.terravista.compat;

import dev.terravista.world.ChunkAdmission;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.concurrent.CompletableFuture;

/** 在 Chunky 区块结果完成时提交自动采集。 */
public final class ChunkyCapture {
    public static CompletableFuture<ChunkResult<ChunkAccess>> observe(
            CompletableFuture<ChunkResult<ChunkAccess>> source) {
        return source.thenApply(result -> {
            result.ifSuccess(chunk -> {
                if (chunk instanceof LevelChunk complete) ChunkAdmission.captureAutomatic(complete);
            });
            return result;
        });
    }
}

package dev.terravista.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.terravista.compat.ChunkyCapture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.popcraft.chunky.mixin.ServerChunkCacheMixin;
import org.popcraft.chunky.platform.NeoForgeWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;

/** 转交 Chunky 主线程区块请求的返回结果。 */
@Mixin(NeoForgeWorld.class)
public class GeneratedChunkBridge {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lorg/popcraft/chunky/mixin/ServerChunkCacheMixin;invokeGetChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> terravista$generatedChunk(
            ServerChunkCacheMixin cache, int x, int z, ChunkStatus status, boolean create,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        return ChunkyCapture.observe(original.call(cache, x, z, status, create));
    }
}

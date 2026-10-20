package dev.terravista.mixin.minecraft;

import dev.terravista.bootstrap.RuntimeEnvironment;
import dev.terravista.compat.UnboundedChunkAccess;
import dev.terravista.world.ClientChunkCapture;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 连接缓存区块访问与卸载前采集。 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheBridge implements UnboundedChunkAccess {
    @Unique private static final boolean terravista$bobby = RuntimeEnvironment.modLoaded("bobby");
    @Shadow private volatile ClientChunkCache.Storage storage;

    @Override public @Nullable LevelChunk terravista$chunk(int x, int z) {
        return ClientChunkCapture.lookup(storage, storage, x, z);
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void terravista$unloading(ChunkPos position, CallbackInfo callback) {
        ClientChunkCapture.unloading(this, position, terravista$bobby);
    }
}

package dev.terravista.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取 Sodium 区块准备状态。 */
@Mixin(value = ChunkTracker.class, remap = false)
public interface ChunkStatusAccess {
    @Accessor("chunkStatus")
    Long2IntOpenHashMap terravista$chunkStatus();
}

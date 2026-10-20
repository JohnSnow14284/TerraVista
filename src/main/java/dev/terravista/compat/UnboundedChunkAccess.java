package dev.terravista.compat;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** 访问客户端缓存中坐标匹配的区块。 */
public interface UnboundedChunkAccess {
    @Nullable LevelChunk terravista$chunk(int x, int z);
}

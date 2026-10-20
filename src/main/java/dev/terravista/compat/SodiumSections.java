package dev.terravista.compat;

import dev.terravista.mixin.sodium.ChunkStatusAccess;
import dev.terravista.world.ChunkAdmission;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 管理 Sodium 区块事件采集与上传状态缓存。 */
public final class SodiumSections {
    private long previousChunk = -1;
    private int previousStatus;
    private int firstSection;

    public void initialize(ClientLevel level) {
        firstSection = level.getMinY() >> 4;
    }

    public static void beginVisibility() {
        var session = RenderSessionAccess.current();
        if (session != null && session.visibleBounds() != null) session.visibleBounds().beginCollection();
    }

    public static void removed(ClientLevel level, int x, int z, boolean bobby) {
        if (!bobby) {
            var chunks = (UnboundedChunkAccess) level.getChunkSource();
            if (chunks != null) {
                var chunk = chunks.terravista$chunk(x, z);
                if (chunk != null) ChunkAdmission.captureAutomatic(chunk);
            }
        }
    }

    public static void added(ClientLevel level, int x, int z) {
        if (level != null) {
            var chunks = level.getChunkSource();
            if (chunks != null) {
                var chunk = chunks.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) ChunkAdmission.captureAutomatic(chunk);
            }
        }
    }

    public int uploaded(ClientLevel level, RenderSection section, BuiltSectionInfo info) {
        boolean invisible = section.isInvisible();
        int changes = section.setInfo(info);
        if (invisible == section.isInvisible() || changes == 0) return changes;
        var session = RenderSessionAccess.current();
        if (session == null) return changes;
        int x = section.getChunkX(), y = section.getChunkY(), z = section.getChunkZ();
        if (!invisible) {
            var tracker = ((ChunkStatusAccess) ChunkTrackerHolder.get(level)).terravista$chunkStatus();
            long key = ChunkPos.pack(x, z);
            if (key != previousChunk) {
                previousChunk = key;
                previousStatus = tracker.getOrDefault(key, 0);
            }
            if (previousStatus == 3) {
                var chunks = level.getChunkSource();
                var chunk = chunks.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    var blocks = chunk.getSection(y - firstSection);
                    var lighting = level.getLightEngine();
                    var position = SectionPos.of(x, y, z);
                    var block = lighting.getLayerListener(LightLayer.BLOCK).getDataLayerData(position);
                    var sky = lighting.getLayerListener(LightLayer.SKY).getDataLayerData(position);
                    ChunkAdmission.raw(session.world(), blocks, x, y, z,
                            block == null ? null : block.copy(), sky == null ? null : sky.copy());
                }
            }
        }
        return changes;
    }
}

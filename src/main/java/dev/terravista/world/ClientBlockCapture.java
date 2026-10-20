package dev.terravista.world;

import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 采集区块边缘变为空气的方块所在节。 */
public final class ClientBlockCapture {
    private int firstSection;

    public void initialize(Level level) {
        firstSection = level.getMinY() >> 4;
    }

    public void changed(Level level, BlockPos position, BlockState previous, BlockState updated) {
        if (previous == updated) return;
        if (!updated.isAir()) return;
        if (SessionLifecycle.current() == null) return;
        var identity = WorldAccess.identity(level);
        if (identity == null) return;

        int x = position.getX() & 15;
        int y = position.getY() & 15;
        int z = position.getZ() & 15;
        if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) return;

        var sectionPosition = SectionPos.of(position);
        var chunk = level.getChunk(position.getX() >> 4, position.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return;
        var section = chunk.getSection(sectionPosition.y() - firstSection);
        var lighting = level.getLightEngine();
        var block = lighting.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPosition);
        var sky = lighting.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPosition);
        ChunkAdmission.raw(identity, section, sectionPosition.x(), sectionPosition.y(), sectionPosition.z(),
                block == null ? null : block.copy(), sky == null ? null : sky.copy());
    }
}

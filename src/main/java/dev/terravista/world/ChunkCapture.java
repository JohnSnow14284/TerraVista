package dev.terravista.world;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

/** 校验区块采集条件并复制光照层后提交摄取队列。 */
public final class ChunkCapture {
    private ChunkCapture() {}

    public static boolean enqueue(ChunkIngestQueue queue, VoxelWorld world, LevelChunk chunk) {
        if (!queue.live()) return false;
        if (!world.live()) throw new IllegalStateException("TerraVista World: Tried inserting chunk into WorldEngine that was not alive");
        world.markActive();
        var lighting = chunk.getLevel().getLightEngine();
        boolean gotLighting = false;
        int y = chunk.getMinSectionY() - 1;
        boolean allEmpty = true;
        for (var section : chunk.getSections()) {
            y++;
            if (section == null) continue;
            allEmpty &= section.hasOnlyAir();
            var position = SectionPos.of(chunk.getPos(), y);
            if (lighting.getDebugSectionType(LightLayer.SKY, position) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA
                    && lighting.getDebugSectionType(LightLayer.BLOCK, position) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) continue;
            gotLighting = true;
        }
        if (allEmpty && !gotLighting) {
            y = chunk.getMinSectionY() - 1;
            for (var section : chunk.getSections()) {
                y++;
                if (section == null) continue;
                if (!queue.enqueue(world, section, chunk.getPos().x(), y, chunk.getPos().z(), null, null)) break;
            }
        }
        if (!gotLighting) return false;
        var blockSource = lighting.getLayerListener(LightLayer.BLOCK);
        var skySource = lighting.getLayerListener(LightLayer.SKY);
        y = chunk.getMinSectionY() - 1;
        for (var section : chunk.getSections()) {
            y++;
            if (section == null) continue;
            var position = SectionPos.of(chunk.getPos(), y);
            var block = blockSource.getDataLayerData(position);
            if (block != null) block = block.copy();
            var sky = skySource.getDataLayerData(position);
            if (sky != null) sky = sky.copy();
            if (!queue.enqueue(world, section, chunk.getPos().x(), y, chunk.getPos().z(), block, sky)) break;
        }
        return true;
    }
}

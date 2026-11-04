package dev.terravista.render.gpu;

import dev.terravista.compat.VitrailBlockIds;
import net.minecraft.world.level.block.Block;
import org.lwjgl.system.MemoryUtil;

/** One small lookup per renderer, refreshed only when Vitrail installs a new block.properties. */
final class PackMaterialTable {
    final GpuBuffer buffer;
    private int generation = Integer.MIN_VALUE;

    PackMaterialTable(FrameCommands commands) {
        buffer = new GpuBuffer(commands, Math.max(16, Block.BLOCK_STATE_REGISTRY.size() * 4L));
    }

    void refresh(VulkanUploads uploads) {
        int installed = VitrailBlockIds.generation();
        if (installed == generation) return;
        long target = uploads.upload(buffer, 0, buffer.sizeBytes());
        MemoryUtil.memSet(target, 0xff, buffer.sizeBytes());
        for (var state : Block.BLOCK_STATE_REGISTRY) {
            MemoryUtil.memPutInt(target + Block.BLOCK_STATE_REGISTRY.getId(state) * 4L, VitrailBlockIds.id(state));
        }
        uploads.commit();
        generation = installed;
    }
}

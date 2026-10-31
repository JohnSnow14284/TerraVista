package dev.terravista.compat;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import org.lwjgl.vulkan.VkCommandBuffer;
import static org.lwjgl.vulkan.VK12.vkCmdDrawIndexedIndirectCount;

/** Preserves the GPU-generated draw count through the host render-pass API. */
public final class VitrailIndirect {
    private static VitrailIndirect current;
    private dev.terravista.render.gpu.GpuBuffer indexOwner, callOwner, counts;
    private BorrowedBuffer indices, calls;
    private long offset, countOffset;
    private int maximum;
    private boolean counted;

    public void submit(RenderPass pass, dev.terravista.render.gpu.GpuBuffer indexBuffer,
            dev.terravista.render.gpu.GpuBuffer drawCalls, dev.terravista.render.gpu.GpuBuffer drawCounts,
            long offset, long countOffset, int maximum, boolean counted) {
        if (maximum <= 0) return;
        if (current != null) throw new IllegalStateException("TerraVista: nested indirect submission");
        if (offset < 0 || offset > drawCalls.sizeBytes() - maximum * 20L
                || countOffset < 0 || countOffset > drawCounts.sizeBytes() - 4) {
            throw new IllegalArgumentException("TerraVista: indirect draw outside allocated range");
        }
        if (indexOwner != indexBuffer) {
            indexOwner = indexBuffer;
            indices = new BorrowedBuffer(indexBuffer.buffer, GpuBuffer.USAGE_INDEX,
                    indexBuffer.sizeBytes(), indexBuffer::isReleased);
        }
        if (callOwner != drawCalls) {
            callOwner = drawCalls;
            calls = new BorrowedBuffer(drawCalls.buffer, GpuBuffer.USAGE_INDIRECT_PARAMETERS,
                    drawCalls.sizeBytes(), drawCalls::isReleased);
        }
        indices.checkCanBeUsed();
        calls.checkCanBeUsed();
        drawCounts.requireLive();
        this.counts = drawCounts;
        this.offset = offset;
        this.countOffset = countOffset;
        this.maximum = maximum;
        this.counted = counted;
        pass.setIndexBuffer(indices, IndexType.INT);
        current = this;
        try { pass.drawIndexedIndirect(calls.slice(offset, maximum * 20L), maximum); }
        finally { current = null; }
    }

    public static boolean counted(VkCommandBuffer command, long buffer, long offset, int count, int stride) {
        VitrailIndirect draw = current;
        if (draw == null || !draw.counted) return false;
        if (buffer != draw.callOwner.buffer || offset != draw.offset || count != draw.maximum || stride != 20) {
            throw new IllegalStateException("TerraVista: unexpected host indirect draw arguments");
        }
        vkCmdDrawIndexedIndirectCount(command, buffer, offset, draw.counts.buffer,
                draw.countOffset, draw.maximum, stride);
        return true;
    }
}

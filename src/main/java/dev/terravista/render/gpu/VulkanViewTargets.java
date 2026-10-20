package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;
import dev.terravista.render.ViewState;

/** 持有单视图的 GPU 资源，相机状态与附件替换分别管理。 */
public final class VulkanViewTargets {
    public static final int OPAQUE_CAPACITY = 400_000;
    public static final int TRANSLUCENT_CAPACITY = 100_000;
    public static final int TEMPORAL_CAPACITY = 100_000;
    public static final int LOOKUP_CAPACITY = 200_000;

    public final ViewState state = new ViewState();
    public final ViewportAttachments attachments;
    public final GpuBuffer drawCounts, drawCalls, positionScratch, lookup, visibility;
    public final DepthPyramid depthPyramid;

    public VulkanViewTargets(FrameCommands commands, DepthConvention convention, int maxSections) {
        attachments = new ViewportAttachments(commands);
        GpuBuffer[] created = new GpuBuffer[5];
        try {
            drawCounts = created[0] = new GpuBuffer(commands, 1024);
            drawCounts.zero();
            drawCalls = created[1] = new GpuBuffer(commands, 5L * 4 * (OPAQUE_CAPACITY + TRANSLUCENT_CAPACITY + TEMPORAL_CAPACITY));
            drawCalls.zero();
            positionScratch = created[2] = new GpuBuffer(commands, 8L * OPAQUE_CAPACITY);
            positionScratch.zero();
            lookup = created[3] = new GpuBuffer(commands, LOOKUP_CAPACITY * 4L + 4);
            lookup.zero();
            visibility = created[4] = new GpuBuffer(commands, maxSections * 4L);
            visibility.zero();
            depthPyramid = new DepthPyramid(commands, convention);
        } catch (RuntimeException | Error failure) {
            // 等待帧回收确认已记录的缓冲填充完成。
            for (var buffer : created) if (buffer != null) buffer.free();
            throw failure;
        }
    }

    public boolean ensureTargets() { return attachments.ensure(state); }
    public GpuBuffer drawList() { return lookup; }

    public void close() {
        attachments.close();
        depthPyramid.close();
        visibility.free();
        lookup.free();
        drawCounts.free();
        drawCalls.free();
        positionScratch.free();
    }
}

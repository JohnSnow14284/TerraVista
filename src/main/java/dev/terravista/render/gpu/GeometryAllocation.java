package dev.terravista.render.gpu;

import dev.terravista.bootstrap.ModLog;

/** 处理几何内存分配失败的重试策略。 */
final class GeometryAllocation {
    private GeometryAllocation() {}
    static GpuBuffer allocate(FrameCommands commands, long capacity) {
        while (true) {
            try {
                ModLog.info("Render: Allocating " + (capacity / (1024 * 1024)) + "MB VK geometry buffer");
                return new GpuBuffer(commands, capacity);
            } catch (VulkanCalls.Failure failure) {
                if (!failure.outOfMemory() || capacity <= (256L << 20)) throw failure;
                capacity = Math.max(256L << 20, (capacity / 2) & ~7L);
                ModLog.warn("Render: VK geometry allocation failed, retrying with " + (capacity / (1024 * 1024)) + "MB");
            }
        }
    }
}

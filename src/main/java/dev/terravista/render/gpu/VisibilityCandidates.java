package dev.terravista.render.gpu;

/** 管理交替归约轮次使用的两份不同容量候选缓冲。 */
final class VisibilityCandidates {
    private final FrameCommands commands;
    GpuBuffer first, second;

    VisibilityCandidates(FrameCommands commands) { this.commands = commands; }

    void ensure(int groups) {
        long bytes = groups * 256L * 8;
        if (first != null && first.size() >= bytes) return;
        var nextFirst = new GpuBuffer(commands, bytes);
        GpuBuffer nextSecond;
        try {
            nextSecond = groups > 2 ? new GpuBuffer(commands, ((groups + 1L) / 2) * 256 * 8) : null;
        } catch (RuntimeException | Error failure) {
            nextFirst.free();
            throw failure;
        }
        if (first != null) first.free();
        if (second != null) second.free();
        first = nextFirst;
        second = nextSecond;
    }

    GpuBuffer target(GpuBuffer input) { return input == first ? second : first; }

    void close() {
        if (first != null) first.free();
        if (second != null) second.free();
    }
}

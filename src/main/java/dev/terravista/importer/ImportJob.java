package dev.terravista.importer;

import dev.terravista.world.VoxelWorld;
import java.util.function.IntConsumer;

/** 定义导入任务的启动、停止与生命周期操作。 */
public interface ImportJob {
    @FunctionalInterface
    interface Progress { void update(int completed, int total); }
    void start(Progress progress, IntConsumer completed);
    VoxelWorld world();
    void shutdown();
    boolean running();
    boolean successful();
}

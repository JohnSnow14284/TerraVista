package dev.terravista.importer;

import dev.terravista.world.VoxelWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** 为每个世界登记单个导入任务并协调完成与取消回调。 */
public class ImportCoordinator {
    private final Map<VoxelWorld, Run> active = new HashMap<>();

    protected class Run {
        protected final ImportJob job;
        protected long lastUpdate;
        protected long updateInterval = 50;

        protected Run(ImportJob job) { this.job = job; lastUpdate = System.currentTimeMillis(); }
        private ImportJob.Progress progress;
        private Consumer<Boolean> result;

        private void start() {
            if (job.running()) throw new IllegalStateException();
            try {
                job.start((done, total) -> {
                    if (updated(done, total) && progress != null) progress.update(done, total);
                }, total -> {
                    completed(total);
                    if (result != null) result.accept(job.successful());
                });
            } catch (RuntimeException exception) {
                ImportCoordinator.this.finished(this);
                throw exception;
            }
        }
        protected boolean updated(int completed, int total) {
            if (System.currentTimeMillis() - lastUpdate < updateInterval) return false;
            lastUpdate = System.currentTimeMillis();
            return true;
        }
        protected void completed(int total) { ImportCoordinator.this.finished(this); }
        protected void shutdown() { job.shutdown(); }
        protected boolean completed() { return !job.running(); }
    }
    protected synchronized Run create(ImportJob job) { return new Run(job); }
    private boolean tryStart(ImportJob job, ImportJob.Progress progress, Consumer<Boolean> result) {
        Run run;
        synchronized (this) {
            var previous = active.get(job.world());
            if (previous != null) {
                if (!previous.completed()) return false;
                throw new IllegalStateException();
            }
            run = create(job);
            run.progress = progress;
            run.result = result;
            active.put(job.world(), run);
        }
        run.start();
        return true;
    }
    public boolean makeIfAbsent(VoxelWorld world, Supplier<ImportJob> factory) {
        return makeIfAbsent(world, factory, null, null);
    }
    public boolean makeIfAbsent(VoxelWorld world, Supplier<ImportJob> factory,
                                ImportJob.Progress progress, Consumer<Boolean> result) {
        try {
            world.acquireRef();
            synchronized (this) { if (active.containsKey(world)) return false; }
            return tryStart(factory.get(), progress, result);
        } finally { world.releaseRef(); }
    }
    public boolean cancel(VoxelWorld world) {
        Run run;
        synchronized (this) {
            run = active.get(world);
            if (run == null) return false;
        }
        run.shutdown();
        synchronized (this) { active.remove(world); }
        return true;
    }
    private synchronized void finished(Run run) {
        var removed = active.remove(run.job.world());
        if (removed != null && removed != run) throw new IllegalStateException();
    }
}

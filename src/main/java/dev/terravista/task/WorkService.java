package dev.terravista.task;

import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 管理加权服务的待处理任务、工作线程绑定与关闭排空。 */
public final class WorkService {
    final long weight;
    final String name;
    final BooleanSupplier limiter;
    private final WeightedScheduler scheduler;
    private final WorkerContexts contexts;
    private final Semaphore pending = new Semaphore(0);
    private volatile boolean live = true;
    private volatile boolean stopping;
    WorkService(Supplier<WorkerBinding> create, WeightedScheduler scheduler, long weight, String name, BooleanSupplier limiter) {
        this.scheduler = scheduler; this.weight = weight; this.name = name; this.limiter = limiter;
        contexts = new WorkerContexts(create, exception -> scheduler.failed(this, exception));
    }
    public synchronized boolean submit() {
        if (stopping) return false;
        scheduler.queued(pending::release);
        return true;
    }
    boolean run() {
        if (stopping || !live || !pending.tryAcquire()) return false;
        try { contexts.run(); return true; }
        finally { scheduler.removed(1); }
    }
    public boolean live() { return live && !stopping; }
    public int pending() { return pending.availablePermits(); }
    public void awaitEmpty() {
        while (live() && pending() != 0) {
            Thread.yield();
            try { Thread.sleep(10); }
            catch (InterruptedException exception) { throw new RuntimeException(exception); }
        }
    }
    public int shutdown() {
        synchronized (this) {
            if (stopping) return 0;
            stopping = true;
        }
        scheduler.detach(this);
        int remaining;
        try { contexts.shutdown(); }
        finally {
            remaining = pending.drainPermits();
            live = false;
            scheduler.removed(remaining);
        }
        return remaining;
    }
    public boolean steal() {
        if (!pending.tryAcquire()) return false;
        scheduler.removed(1); return true;
    }
    public int drain() {
        int count = pending.drainPermits();
        if (count != 0) scheduler.removed(count);
        return count;
    }
}

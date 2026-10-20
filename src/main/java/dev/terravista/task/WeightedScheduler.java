package dev.terravista.task;

import dev.terravista.bootstrap.ModLog;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** 发布活动服务，按权重选择任务并统计全局作业。 */
public final class WeightedScheduler {
    private static final class SelectionCursor {
        int round;
        long entropy = HashCommon.murmurHash3(System.nanoTime() ^ System.identityHashCode(this));
        long sample(long weight) { return (entropy = HashCommon.mix(entropy)) % weight; }
    }
    private final IntConsumer publishJobs;
    private final ThreadLocal<SelectionCursor> selection = ThreadLocal.withInitial(SelectionCursor::new);
    private final AtomicInteger jobs = new AtomicInteger();
    private volatile WorkService[] services = new WorkService[0];
    private volatile boolean stopped;

    public WeightedScheduler(IntConsumer publishJobs) { this.publishJobs = publishJobs; }
    public WorkService registerUnmanaged(Supplier<Runnable> create, long weight) { return registerUnmanaged(create, weight, ""); }
    public WorkService registerUnmanaged(Supplier<Runnable> create, long weight, String name) {
        return register(() -> WorkerBinding.unmanaged(create.get()), weight, name);
    }
    public WorkService register(Supplier<WorkerBinding> create, long weight) { return register(create, weight, ""); }
    public WorkService register(Supplier<WorkerBinding> create, long weight, String name) { return register(create, weight, name, null); }
    public synchronized WorkService register(Supplier<WorkerBinding> create, long weight, String name, BooleanSupplier limiter) {
        var service = new WorkService(create, this, weight, name, limiter);
        var published = Arrays.copyOf(services, services.length + 1);
        published[published.length - 1] = service;
        services = published;
        return service;
    }
    public int runOne() {
        if (services.length == 0 || jobs.get() == 0) return 1;
        return selectAndRun();
    }
    private int selectAndRun() {
        if (services.length == 0) return 1;
        var cursor = selection.get();
        retry:
        for (;;) {
            long skipped = 0;
            var snapshot = services;
            if (snapshot.length == 0 || jobs.get() == 0) return 1;
            long weight = 0;
            int offset = (cursor.round++ & Integer.MAX_VALUE) % snapshot.length;
            WorkService chosen = null;
            for (int i = 0; i < snapshot.length; i++) {
                var service = snapshot[(i + offset) % snapshot.length];
                if (!service.live()) { Thread.yield(); continue retry; }
                if (service.limiter != null && !service.limiter.getAsBoolean()) { skipped |= 1L << i; continue; }
                long count = service.pending();
                if (count != 0 && chosen == null) chosen = service;
                weight += count * service.weight;
            }
            if (weight == 0) return skipped != 0 ? 3 : 2;
            // 按有符号余数与非正条件选择服务。
            long sample = cursor.sample(weight);
            for (int i = 0; i < snapshot.length; i++) {
                var service = snapshot[(i + offset) % snapshot.length];
                if (service.limiter != null && ((skipped & (1L << i)) != 0 || !service.limiter.getAsBoolean())) {
                    skipped |= 1L << i; continue;
                }
                sample -= service.pending() * service.weight;
                if (sample <= 0) { chosen = service; break; }
            }
            if (chosen == null) return skipped != 0 ? 3 : 2;
            if (!chosen.live() || !chosen.run()) continue;
            return 0;
        }
    }
    public void shutdown() {
        if (stopped) throw new IllegalStateException("TerraVista Tasks: Service manager already shutdown");
        stopped = true;
        while (services.length != 0) {
            Thread.yield();
            synchronized (this) {
                for (var service : services) if (service.live()) throw new IllegalStateException("TerraVista Tasks: Service '" + service.name + "' was not in shutdown when manager shutdown");
            }
        }
        while (jobs.get() != 0) Thread.yield();
    }
    synchronized void detach(WorkService service) {
        var previous = services;
        var published = new WorkService[previous.length - 1];
        int cursor = 0;
        for (var entry : previous) if (entry != service) published[cursor++] = entry;
        if (cursor != published.length) throw new IllegalStateException("TerraVista Tasks: Could not find the service in the services array");
        services = published;
    }
    void queued(Runnable makeAvailable) { jobs.incrementAndGet(); makeAvailable.run(); publishJobs.accept(1); }
    void removed(int count) { if (jobs.addAndGet(-count) < 0) throw new IllegalStateException("TerraVista Tasks: total jobs <0"); }
    void failed(WorkService service, Exception exception) {
        ModLog.error("Tasks: Service '" + service.name + "' on thread '" + Thread.currentThread().getName() + "' had an exception", exception);
    }
}

package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.task.WorkService;
import dev.terravista.task.WorkerBinding;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** 管理待保存区段的引用、排队与背压。 */
public final class SectionSaveQueue {
    private static final int SOFT_LIMIT = 5_000;
    private static final ThreadLocal<Boolean> DRAINING = ThreadLocal.withInitial(() -> false);
    private record PendingSave(VoxelWorld world, ResidentSection section) {}
    private final WorkService service;
    private final ConcurrentLinkedDeque<PendingSave> queue = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<ResidentSection, RuntimeException> failures = new ConcurrentHashMap<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile long retryAfter;
    private volatile long nextFailureLog;

    public SectionSaveQueue(WeightedScheduler scheduler) {
        service = scheduler.register(() -> WorkerBinding.unmanaged(this::runNext), 100, "TerraVista section saves",
                () -> retryAfter == 0 || System.nanoTime() - retryAfter >= 0);
    }
    private void runNext() {
        boolean previous = DRAINING.get();
        DRAINING.set(true);
        PendingSave save = null;
        boolean retained = false;
        try {
            save = queue.remove();
            var section = save.section;
            synchronized (section) {
                try {
                    if (section.exchangeQueued(false)) {
                        section.clearDirty();
                        save.world.storage.save(section.contents());
                        failures.remove(section);
                    }
                } catch (RuntimeException exception) {
                    section.restoreDirty();
                    failures.put(section, exception);
                    long now = System.nanoTime();
                    retryAfter = now + 250_000_000L;
                    if (section.exchangeQueued(true)) {
                        queue.add(save);
                        retained = true;
                        service.submit();
                    }
                    if (now - nextFailureLog >= 0) {
                        nextFailureLog = now + 5_000_000_000L;
                        ModLog.error("World: Section save failed; pending data retained for retry", exception);
                    }
                }
            }
        } finally {
            try {
                if (save != null && !retained) {
                    try { save.section.release(); }
                    finally { outstanding.decrementAndGet(); }
                }
            }
            finally { DRAINING.set(previous); }
        }
    }
    public boolean enqueue(VoxelWorld world, ResidentSection section, boolean nonBlocking, boolean acquired) {
        synchronized (this) {
            if (!service.live() || !section.exchangeQueued(true)) return false;
            if (!acquired) section.acquire();
            outstanding.incrementAndGet();
            queue.add(new PendingSave(world, section));
            service.submit();
        }
        if (!nonBlocking && !DRAINING.get()) {
            while (pending() > SOFT_LIMIT && service.live()) {
                if (!service.steal()) break;
                runNext();
                if (!failures.isEmpty()) break;
            }
        }
        return true;
    }
    public void shutdown() {
        for (;;) {
            if (!service.live()) return;
            checkFailure();
            synchronized (this) {
                if (outstanding.get() == 0) {
                    service.shutdown();
                    return;
                }
            }
            LockSupport.parkNanos(10_000_000L);
            if (Thread.currentThread().isInterrupted())
                throw new IllegalStateException("TerraVista World: Interrupted while waiting for section saves");
        }
    }
    void checkFailure() {
        var failure = failures.values().stream().findFirst();
        if (failure.isPresent()) throw new IllegalStateException("TerraVista World: Pending section data could not be saved; session retained for retry", failure.get());
    }
    public int pending() { return service.pending(); }
}

package dev.terravista.task;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.TrackedResource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 按共享线程标识管理各工作线程的执行与清理上下文。 */
public final class WorkerContexts extends TrackedResource {
    private record ThreadIdentity(long id) implements LongSupplier {
        private static final AtomicLong NEXT = new AtomicLong();
        ThreadIdentity() { this(NEXT.getAndIncrement()); }
        @Override public long getAsLong() { return id; }
    }
    private record ThreadBinding(Runnable job, Runnable cleanup, boolean ownedCleanup) {
        ThreadBinding(WorkerBinding binding) { this(binding.execute(), binding.cleanup(), binding.hasCleanup()); }
        void release() { if (ownedCleanup) cleanup.run(); }
    }
    private static final ThreadLocal<ThreadIdentity> THREAD = ThreadLocal.withInitial(ThreadIdentity::new);
    private final WeakThreadContexts<ThreadIdentity,ThreadBinding> bindings = new WeakThreadContexts<>(this::releaseCollected);
    private final Supplier<ThreadBinding> create;
    private final Consumer<Exception> failed;
    private final AtomicInteger running = new AtomicInteger();
    private volatile boolean live = true;

    WorkerContexts(Supplier<WorkerBinding> create) {
        this(create, exception -> ModLog.error("Tasks: Background executor task failed", exception));
    }
    WorkerContexts(Supplier<WorkerBinding> create, Consumer<Exception> failed) {
        this.create = () -> new ThreadBinding(create.get());
        this.failed = failed;
    }
    private void releaseCollected(ThreadBinding binding) {
        try { binding.release(); }
        catch (Exception exception) { failed.accept(exception); }
    }
    boolean run() {
        running.incrementAndGet();
        try {
            if (!live) {
                failed.accept(new IllegalStateException("TerraVista Tasks: Executor is in shutdown"));
                return false;
            }
            var binding = bindings.getOrCreate(THREAD.get(), create);
            binding.job().run();
            return true;
        } catch (Exception exception) {
            failed.accept(exception);
            return true;
        } finally {
            running.decrementAndGet();
        }
    }
    public synchronized void shutdown() {
        if (!live) return;
        live = false;
        while (running.get() != 0) java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        RuntimeException failure = null;
        for (var binding : bindings.clear()) {
            try { binding.release(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        markReleased();
        if (failure != null) throw failure;
    }
    @Override public void free() { shutdown(); }
    public boolean live() { return live; }
}

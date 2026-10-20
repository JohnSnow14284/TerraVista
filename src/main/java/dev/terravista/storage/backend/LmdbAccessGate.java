package dev.terravista.storage.backend;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 协调事务接收与独占映射扩容重试。 */
final class LmdbAccessGate {
    private final AtomicInteger active = new AtomicInteger();
    private final ReentrantLock resizing = new ReentrantLock();
    private final Runnable grow;
    LmdbAccessGate(Runnable grow) { this.grow = grow; }

    <T> T access(Supplier<T> operation) {
        try {
            active.getAndAdd(1);
            while (resizing.isLocked()) {
                active.getAndAdd(-1);
                while (resizing.isLocked()) Thread.onSpinWait();
                active.getAndAdd(1);
            }
            return operation.get();
        } finally { active.getAndAdd(-1); }
    }
    <T> T growing(Supplier<T> operation) {
        for (;;) {
            try { return access(operation); }
            catch (Throwable failure) {
                if (failure.getMessage().startsWith("Code: -30792")) {
                    if (resizing.tryLock()) {
                        while (active.get() != 0) Thread.onSpinWait();
                        grow.run();
                        resizing.unlock();
                    }
                } else throw failure;
            }
        }
    }
}

package dev.terravista.world;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** 由单个加载者发布区块并为等待者预留引用。 */
final class SectionLoadTicket {
    private static final VarHandle BEFORE;
    private static final VarHandle AFTER;
    static {
        try {
            BEFORE = MethodHandles.lookup().findVarHandle(SectionLoadTicket.class, "before", int.class);
            AFTER = MethodHandles.lookup().findVarHandle(SectionLoadTicket.class, "after", int.class);
        } catch (NoSuchFieldException | IllegalAccessException exception) { throw new RuntimeException(exception); }
    }
    private volatile int before;
    private volatile int after;
    volatile ResidentSection section;
    private volatile RuntimeException failure;

    void registerWaiter() { BEFORE.getAndAdd(this, 1); }
    void fail(RuntimeException exception) { failure = exception; }
    void publish(ResidentSection loaded) {
        int waiting = (int) BEFORE.getAndSet(this, 0);
        loaded.acquire(waiting);
        AFTER.set(this, waiting);
        VarHandle.storeStoreFence();
        section = loaded;
        VarHandle.releaseFence();
    }
    ResidentSection await() {
        ResidentSection loaded;
        VarHandle.fullFence();
        while ((loaded = section) == null) {
            var exception = failure;
            if (exception != null) throw exception;
            VarHandle.fullFence();
            Thread.onSpinWait();
            Thread.yield();
        }
        if (0 < ((int) AFTER.getAndAdd(this, -1))) return loaded;
        return loaded.tryAcquire() ? loaded : null;
    }
}

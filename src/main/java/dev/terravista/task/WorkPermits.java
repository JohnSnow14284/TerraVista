package dev.terravista.task;

import dev.terravista.memory.TrackedResource;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** 为专用线程与 Sodium 工作线程分配优先本地通道的共享许可。 */
public final class WorkPermits {
    public static final class Channel extends TrackedResource {
        private final WorkPermits owner;
        private final Semaphore wakeups = new Semaphore(0);
        private final Semaphore local = new Semaphore(0);
        private Channel(WorkPermits owner) { this.owner = owner; }

        public void release(int count) {
            local.release(count);
            wakeups.release(count);
        }
        public void acquire() { acquire(true); }
        public void acquire(boolean contribute) {
            if (!contribute) {
                local.acquireUninterruptibly();
                wakeups.tryAcquire();
                return;
            }
            for (;;) {
                wakeups.acquireUninterruptibly();
                if (local.tryAcquire() || owner.contribute(this)) return;
            }
        }
        public boolean tryAcquire() {
            if (local.availablePermits() == 0 || !wakeups.tryAcquire()) return false;
            if (local.tryAcquire()) return true;
            wakeups.release();
            return false;
        }
        public int available() { return local.availablePermits(); }
        @Override public void free() {
            owner.remove(this);
            markReleased();
        }
    }

    private final Semaphore shared = new Semaphore(0);
    private final IntSupplier execute;
    private volatile Channel[] channels = new Channel[0];
    public WorkPermits(IntSupplier execute) { this.execute = execute; }
    public synchronized Channel register() {
        var channel = new Channel(this);
        var published = Arrays.copyOf(channels, channels.length + 1);
        published[published.length - 1] = channel;
        channels = published;
        return channel;
    }
    private synchronized void remove(Channel channel) {
        var previous = channels;
        var published = new Channel[previous.length - 1];
        int cursor = 0;
        for (Channel entry : previous) if (entry != channel) published[cursor++] = entry;
        if (cursor != published.length) throw new IllegalStateException("TerraVista Tasks: Could not find the service in the services array");
        channels = published;
    }
    public void releaseShared(int count) {
        shared.release(count);
        for (Channel channel : channels) channel.wakeups.release(count);
    }
    private boolean contribute(Channel channel) {
        if (!shared.tryAcquire()) return false;
        for (;;) {
            int status = execute.getAsInt();
            if (status == 0 || status == 1) return false;
            if (status >= 2) {
                try {
                    if (channel.local.tryAcquire(10, TimeUnit.MILLISECONDS)) {
                        channel.wakeups.tryAcquire();
                        releaseShared(1);
                        return true;
                    }
                } catch (InterruptedException exception) {
                    throw new RuntimeException(exception);
                }
            }
        }
    }
}

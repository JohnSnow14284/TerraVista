package dev.terravista.compat;

import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.task.WorkPermits;
import java.util.concurrent.Semaphore;

/** 管理 Sodium 任务队列借用的共享工作许可。 */
public final class SodiumQueuePermits {
    private WorkPermits.Channel channel;

    public Semaphore create(int permits) {
        var session = SessionLifecycle.current();
        if (session != null) {
            channel = session.workers().permits.register();
            return new SodiumWorkSemaphore(channel);
        }
        return new Semaphore(permits);
    }

    public void release() {
        if (channel != null) channel.free();
    }
}

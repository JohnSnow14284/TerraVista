package dev.terravista.compat;

import dev.terravista.task.WorkPermits;
import java.util.concurrent.Semaphore;

/** 连接 Sodium 的四个工作许可操作。 */
public final class SodiumWorkSemaphore extends Semaphore {
    private final WorkPermits.Channel channel;
    public SodiumWorkSemaphore(WorkPermits.Channel channel) { super(0); this.channel = channel; }
    @Override public void release(int permits) { channel.release(permits); }
    @Override public void acquire() throws InterruptedException { channel.acquire(); }
    @Override public boolean tryAcquire() { return channel.tryAcquire(); }
    @Override public int availablePermits() { return channel.available(); }
}

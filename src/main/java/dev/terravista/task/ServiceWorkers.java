package dev.terravista.task;

import java.util.ArrayList;
import java.util.List;

/** 以守护线程接入与 Sodium 共享的许可组。 */
public final class ServiceWorkers {
    public final WeightedScheduler scheduler;
    public final WorkPermits permits;
    private final WorkPermits.Channel workers;
    private final ThreadGroup group = new ThreadGroup("TerraVista background services");
    private final List<Thread> threads = new ArrayList<>();
    private int nextId;
    public ServiceWorkers() {
        scheduler = new WeightedScheduler(this::publish);
        permits = new WorkPermits(scheduler::runOne);
        workers = permits.register();
    }
    private void publish(int count) { permits.releaseShared(count); }
    public boolean resize(int count) {
        synchronized (threads) {
            int difference = count - threads.size();
            if (difference == 0) return false;
            if (difference < 0) workers.release(-difference);
            else for (int i = 0; i < difference; i++) {
                var thread = new Thread(group, this::run, "TerraVista worker #" + nextId++);
                thread.setPriority(3); thread.setDaemon(true); threads.add(thread); thread.start();
            }
        }
        for (;;) {
            synchronized (threads) { if (threads.size() == count) return true; }
            try { Thread.sleep(50); }
            catch (InterruptedException exception) { throw new RuntimeException(exception); }
        }
    }
    private void run() {
        try { workers.acquire(); }
        finally { synchronized (threads) { threads.remove(Thread.currentThread()); } }
    }
    public void shutdown() {
        scheduler.shutdown(); workers.release(10000);
        for (;;) {
            synchronized (threads) { if (threads.isEmpty()) break; }
            try { Thread.sleep(100); }
            catch (InterruptedException exception) { throw new RuntimeException(exception); }
        }
        workers.free();
    }
}

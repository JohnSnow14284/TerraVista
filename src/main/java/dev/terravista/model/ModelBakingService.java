package dev.terravista.model;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.world.BiomePaletteEntry;
import dev.terravista.world.StatePalette;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;

/** 管理模型工作线程、请求接收及渲染线程的上传与关闭。 */
public final class ModelBakingService {
    private final ModelStore storage;
    public final ModelCatalog catalog;
    private final StatePalette palette;
    private final Thread worker;
    private volatile boolean running = true;
    private volatile Throwable workerFailure;
    private boolean closed;
    private final ReentrantLock seenLock = new ReentrantLock();
    private final ReentrantLock enqueueLock = new ReentrantLock();
    private final IntOpenHashSet seen = new IntOpenHashSet(6000);
    private final AtomicLong bakeAllowance = new AtomicLong(900_000);

    public ModelBakingService(StatePalette palette, ModelStore storage) {
        this.storage = storage;
        this.palette = palette;
        catalog = new ModelCatalog(palette, storage);
        worker = new Thread(() -> {
            while (running) {
                while (running && bakeAllowance.get() > 0) {
                    long start = System.nanoTime();
                    if (!catalog.process()) break;
                    bakeAllowance.addAndGet(-(System.nanoTime() - start));
                }
                LockSupport.park();
            }
        }, "TerraVista model baker");
        worker.setUncaughtExceptionHandler((thread, failure) -> {
            running = false;
            if (failure == null) failure = new RuntimeException("TerraVista Models: Model worker stopped without an exception object");
            workerFailure = failure;
        });
        worker.start();
    }
    public void tick(long totalBudget) {
        if (workerFailure != null) throw new RuntimeException(workerFailure);
        catalog.upload();
        bakeAllowance.getAndUpdate(remaining -> Math.min(Math.max(remaining, 0) + totalBudget, totalBudget * 2));
        LockSupport.unpark(worker);
    }
    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        enqueueLock.lock();
        try { running = false; }
        finally { enqueueLock.unlock(); }
        LockSupport.unpark(worker);
        try { worker.join(); }
        catch (InterruptedException failure) { throw new RuntimeException(failure); }
        try { catalog.free(); }
        finally { storage.free(); }
    }
    public void request(int blockId) {
        if (!running) return;
        if (blockId < 0 || palette.blockCount() <= blockId) {
            ModLog.error("Models: Bake request exceeds the block-state palette: " + blockId
                    + " max id: " + palette.blockCount(), new Exception());
            return;
        }
        seenLock.lock();
        try { if (!seen.add(blockId)) return; }
        finally { seenLock.unlock(); }
        enqueueLock.lock();
        try { if (running) catalog.request(blockId); }
        catch (RuntimeException | Error exception) {
            seenLock.lock();
            try { seen.remove(blockId); }
            finally { seenLock.unlock(); }
            throw exception;
        } finally { enqueueLock.unlock(); }
        LockSupport.unpark(worker);
    }
    public void addBiome(BiomePaletteEntry biome) {
        catalog.addBiome(biome);
        LockSupport.unpark(worker);
    }
    public void addDebugData(List<String> debug) {
        debug.add(String.format("TerraVista models pending/baked: %03d, %04d", catalog.pendingCount(), catalog.bakedCount()));
    }
    public ModelStore store() { return storage; }
    public boolean queuesEmpty() { return catalog.pendingCount() == 0; }
    public int processingCount() { return catalog.pendingCount(); }
}

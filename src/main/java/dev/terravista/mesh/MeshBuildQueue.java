package dev.terravista.mesh;

import dev.terravista.model.ModelBakingService;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.task.WorkService;
import dev.terravista.task.WorkerBinding;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

/** 接收网格请求，按优先级调度并发布索引与关闭工作线程。 */
public final class MeshBuildQueue {
    static final int RETAIN_LIMIT = 1000;
    public static final AtomicInteger FAILURE_COUNT = new AtomicInteger();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final class Request {
        ResidentSection section;
        final long position;
        final int spatialRank;
        boolean requestedInner, requestedOuter;
        int attempts, addition;
        long priority = Long.MIN_VALUE;
        Request(long position, int spatialRank) { this.position = position; this.spatialRank = spatialRank; }
        void rank() {
            int sequence = SEQUENCE.incrementAndGet();
            int level = Math.min(VoxelWorld.MAX_LEVEL - SectionCoordinates.level(position), 3);
            priority = spatialRank < 0 ? MeshJobPriority.nativeOrder(level, attempts, addition, sequence)
                    : MeshJobPriority.spatialOrder(spatialRank, attempts, addition, sequence);
            addition = 0;
        }
    }
    final AtomicInteger retained = new AtomicInteger();
    final AtomicInteger queued = new AtomicInteger();
    final PriorityBlockingQueue<Request> pending = new PriorityBlockingQueue<>(5000,
            (a, b) -> Long.compareUnsigned(a.priority, b.priority));
    private final StampedLock indexLock = new StampedLock();
    private final Long2ObjectOpenHashMap<Request> index = new Long2ObjectOpenHashMap<>(5000);
    final VoxelWorld world;
    final ModelBakingService models;
    final boolean meshlets;
    Consumer<SectionMesh> consumer;
    final WorkService service;
    private boolean stopping;
    private long lastChanged;

    public MeshBuildQueue(VoxelWorld world, ModelBakingService models, WeightedScheduler scheduler, boolean meshlets) {
        this.world = world; this.models = models; this.meshlets = meshlets;
        service = scheduler.register(() -> {
            var job = new MeshBuildJob(this);
            return new WorkerBinding(job::run, job::free);
        }, 10, "TerraVista mesh builds");
    }
    public void consumer(Consumer<SectionMesh> consumer) { this.consumer = consumer; }
    public void enqueue(long position) { enqueue(position, -1); }
    public void enqueue(long position, int spatialRank) {
        long stamp = indexLock.writeLock();
        try {
            if (stopping || index.containsKey(position)) return;
            var request = new Request(position, spatialRank);
            index.put(position, request);
            request.rank(); queued.incrementAndGet(); pending.add(request); service.submit();
        } finally { indexLock.unlockWrite(stamp); }
    }
    void removeForWork(Request request) {
        long stamp = indexLock.writeLock();
        try { if (index.remove(request.position) != request) throw new IllegalStateException(); }
        finally { indexLock.unlockWrite(stamp); }
    }
    Request restore(Request request) {
        long stamp = indexLock.writeLock();
        try { return index.putIfAbsent(request.position, request); }
        finally { indexLock.unlockWrite(stamp); }
    }
    void discard(Request request) {
        long stamp = indexLock.writeLock();
        try { index.remove(request.position, request); }
        finally { indexLock.unlockWrite(stamp); }
    }
    void requeue(Request request) {
        request.rank(); queued.incrementAndGet(); pending.add(request);
        if (service.live()) service.submit();
    }
    public void shutdown() {
        long stamp = indexLock.writeLock();
        try {
            if (stopping) return;
            stopping = true;
        } finally { indexLock.unlockWrite(stamp); }
        service.shutdown();
        while (!pending.isEmpty()) {
            var request = pending.remove(); queued.decrementAndGet();
            if (request.section != null) { request.section.release(); retained.decrementAndGet(); }
            removeForWork(request);
        }
        if (queued.get() != 0) throw new IllegalStateException();
    }
    public void addDebugData(List<String> debug) {
        if (System.currentTimeMillis() - lastChanged > 100) {
            FAILURE_COUNT.set(0); lastChanged = System.currentTimeMillis();
        }
        debug.add("TerraVista mesh queue/failures: " + queued.get() + "/" + FAILURE_COUNT.get());
    }
    public int taskCount() { return queued.get(); }
}

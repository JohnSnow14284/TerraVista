package dev.terravista.render.tree;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.mesh.SectionMesh;
import dev.terravista.world.ResidentSection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.StampedLock;

/** 管理工作线程输入、根操作抵消与唤醒计数。 */
final class TreeInbox {
    final ConcurrentLinkedDeque<NativeBuffer> requests = new ConcurrentLinkedDeque<>();
    final ConcurrentLinkedDeque<ResidentSection> children = new ConcurrentLinkedDeque<>();
    final ConcurrentLinkedDeque<SectionMesh> meshes = new ConcurrentLinkedDeque<>();
    final ConcurrentLinkedDeque<NativeBuffer> removals = new ConcurrentLinkedDeque<>();
    final AtomicInteger work = new AtomicInteger();
    private final StampedLock rootLock = new StampedLock();
    private final LongOpenHashSet rootAdds = new LongOpenHashSet();
    private final LongOpenHashSet rootRemoves = new LongOpenHashSet();
    final Thread worker;
    volatile boolean running = true;
    volatile Throwable failure;

    record Roots(LongOpenHashSet added, LongOpenHashSet removed) {}
    TreeInbox(Thread worker) { this.worker = worker; }

    private void wake() {
        LockSupport.unpark(worker);
    }
    synchronized void request(NativeBuffer batch) {
        if (!running) { batch.free(); return; }
        work.incrementAndGet(); requests.add(batch); wake();
    }
    synchronized void removal(NativeBuffer batch) {
        if (!running) { batch.free(); return; }
        work.incrementAndGet(); removals.add(batch); wake();
    }
    synchronized void child(ResidentSection section) {
        if (!running) return;
        section.acquire();
        work.incrementAndGet(); children.add(section); wake();
    }
    synchronized void mesh(SectionMesh mesh) {
        if (!running) { mesh.free(); return; }
        work.incrementAndGet(); meshes.add(mesh); wake();
    }
    void addRoot(long position) { updateRoot(position, true); }
    void removeRoot(long position) { updateRoot(position, false); }
    private synchronized void updateRoot(long position, boolean add) {
        if (!running) return;
        long stamp = rootLock.writeLock();
        try {
            var primary = add ? rootAdds : rootRemoves;
            var opposite = add ? rootRemoves : rootAdds;
            int delta = opposite.remove(position) ? -1 : primary.add(position) ? 1 : 0;
            if (delta != 0) if (work.getAndAdd(delta) == 0) LockSupport.unpark(worker);
        } finally { rootLock.unlockWrite(stamp); }
    }
    Roots takeRoots() {
        LongOpenHashSet added = null, removed = null;
        long stamp = rootLock.writeLock();
        try {
            if (!rootAdds.isEmpty()) { added = new LongOpenHashSet(rootAdds); rootAdds.clear(); }
            if (!rootRemoves.isEmpty()) { removed = new LongOpenHashSet(rootRemoves); rootRemoves.clear(); }
        } finally { rootLock.unlockWrite(stamp); }
        return new Roots(added, removed);
    }
    synchronized void failed(Throwable failure) {
        if (failure == null) failure = new RuntimeException("TerraVista Render: null throwable");
        this.failure = failure;
        running = false;
    }
    synchronized void stopAccepting() {
        running = false;
    }
    void discardQueued() {
        NativeBuffer buffer;
        while ((buffer = requests.poll()) != null) buffer.free();
        while ((buffer = removals.poll()) != null) buffer.free();
        SectionMesh mesh;
        while ((mesh = meshes.poll()) != null) mesh.free();
        ResidentSection section;
        while ((section = children.poll()) != null) section.release();
        rootAdds.clear(); rootRemoves.clear(); work.set(0);
    }
}

package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import org.lwjgl.system.MemoryUtil;
import java.util.concurrent.locks.LockSupport;

/** 按顺序处理输入批次并发布合并后的树变更。 */
final class TreeWorker implements Runnable {
    private final TreeInbox inbox;
    private final RenderTree tree;
    private final MeshAllocationQueue allocation;
    private final TreeOutput output;
    private final long geometryCapacity;

    TreeWorker(TreeInbox inbox, RenderTree tree, MeshAllocationQueue allocation,
               TreeOutput output, long geometryCapacity) {
        this.inbox = inbox;
        this.tree = tree;
        this.allocation = allocation;
        this.output = output;
        this.geometryCapacity = geometryCapacity;
    }
    @Override public void run() {
        try {
            while (inbox.running) iterate();
        } catch (Exception failure) {
            ModLog.error("Render: Asynchronous tree processing failed; rendering may be incomplete", failure);
            throw failure;
        }
    }
    void iterate() {
        if (inbox.work.get() <= 0) {
            LockSupport.park();
            if (inbox.work.get() <= 0 || !inbox.running) return;
            sleep(10);
        }
        if (!inbox.running) return;
        int done = roots();
        done += children();
        done += meshes();
        done += requests();
        done += removals();
        if (inbox.work.addAndGet(-done) < 0) {
            sleep(1000);
            if (inbox.work.get() < 0) ModLog.error("Render: Tree work counter is negative");
        }
        if (done == 0) return;
        output.publish(inbox);
    }
    private int roots() {
        var roots = inbox.takeRoots();
        int done = 0;
        if (roots.removed() != null) {
            var iterator = roots.removed().longIterator();
            while (iterator.hasNext()) { tree.removeRoot(iterator.nextLong()); done++; }
        }
        if (roots.added() != null) {
            var iterator = roots.added().longIterator();
            while (iterator.hasNext()) { tree.insertRoot(iterator.nextLong()); done++; }
        }
        return done;
    }
    private int children() {
        int done = 0;
        while (true) {
            var section = inbox.children.poll();
            if (section == null) return done;
            done++;
            try { tree.childrenChanged(section.contents().key, section.contents().childMask()); }
            finally { section.release(); }
        }
    }
    private int meshes() {
        int done = 0;
        long estimatedUpload = 0;
        for (int count = 0; count < 300 && geometryCapacity - allocation.usedBytes() > 50_000_000L
                && estimatedUpload < (1000L << 10); count++) {
            var mesh = inbox.meshes.poll();
            if (mesh == null) break;
            done++;
            tree.geometryResult(mesh);
            if (mesh.geometry != null) estimatedUpload += mesh.geometry.bytes;
        }
        return done;
    }
    private int requests() {
        int done = 0;
        while (true) {
            var batch = inbox.requests.poll();
            if (batch == null) return done;
            done++;
            try {
                if (batch.bytes < 8) throw new IllegalStateException("TerraVista Render: Incomplete node request batch");
                long pointer = batch.address;
                int count = MemoryUtil.memGetInt(pointer);
                pointer += 8;
                if (count < 0 || batch.bytes < count * 8L + 8) throw new IllegalStateException("TerraVista Render: Invalid node request count");
                for (int index = 0; index < count; index++) {
                    long position = position(pointer);
                    pointer += 8;
                    tree.request(position);
                }
            } finally { batch.free(); }
        }
    }
    private int removals() {
        int done = 0;
        while (true) {
            var batch = inbox.removals.poll();
            if (batch == null) return done;
            done++;
            try {
                if (batch.bytes < VisibilityEviction.OUTPUT_COUNT * 8L)
                    throw new IllegalStateException("TerraVista Render: Incomplete node removal batch");
                long pointer = batch.address;
                int zeroCount = 0;
                for (int index = 0; index < VisibilityEviction.OUTPUT_COUNT; index++) {
                    long position = position(pointer);
                    pointer += 8;
                    if (position == -1) continue;
                    if (position == 0 && zeroCount++ > 0) {
                        ModLog.error("Render: Node removal returned a zero position " + zeroCount + " times; include this count in a bug report");
                        continue;
                    }
                    tree.removeGeometry(position);
                }
            } finally { batch.free(); }
        }
    }
    private static long position(long pointer) {
        return ((long) MemoryUtil.memGetInt(pointer) << 32) | Integer.toUnsignedLong(MemoryUtil.memGetInt(pointer + 4));
    }
    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
    }
}

package dev.terravista.render.tree;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.config.ClientOptions;
import dev.terravista.mesh.MeshBuildQueue;
import dev.terravista.render.DeviceBuffer;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/** 管理树工作线程生命周期并连接世界、网格输入与渲染发布。 */
public final class TreePipeline {
    private final TreePublishQueue publications = new TreePublishQueue();
    private final Thread thread;
    private final TreeInbox inbox;
    private final SectionGeometryStorage storage;
    private final NodeUploadTarget uploads;
    private final long geometryCapacity;
    private final SectionSubscriptions subscriptions;
    private final MeshAllocationQueue allocation;
    private final RenderTree tree;
    private final TreeOutput output;
    private final TreeWorker worker;
    private final TreeRenderSync render;
    public final int maxNodes;
    private boolean stopped;

    public TreePipeline(int maxNodes, SectionGeometryStorage storage, MeshBuildQueue meshes, NodeUploadTarget uploads) {
        this.uploads = uploads;
        this.storage = storage;
        geometryCapacity = ((MeshBufferStorage) storage).geometryBytes();
        this.maxNodes = maxNodes;
        thread = new Thread(this::runWorker);
        inbox = new TreeInbox(thread);
        thread.setUncaughtExceptionHandler((thread, failure) -> inbox.failed(failure));
        thread.setName("TerraVista tree updates");
        allocation = new MeshAllocationQueue(((MeshBufferStorage) storage).maxSections(), geometryCapacity);
        subscriptions = new SectionSubscriptions();
        subscriptions.callbacks(meshes::enqueue, meshes::enqueue, inbox::child);
        meshes.consumer(inbox::mesh);
        tree = new RenderTree(maxNodes, allocation, subscriptions);
        output = new TreeOutput(tree, allocation, publications);
        tree.cleaner(new RenderTree.Cleaner() {
            @Override public void allocated(int id) { output.allocated(id); }
            @Override public void moved(int from, int to) {
                // 跳过节点移动对应的可见性清理回调。
            }
            @Override public void freed(int id) { output.freed(id); }
        });
        tree.rootCallbacks(output::rootAdded, output::rootRemoved);
        worker = new TreeWorker(inbox, tree, allocation, output, geometryCapacity);
        render = new TreeRenderSync(inbox, publications, storage, uploads);
    }
    private void runWorker() { worker.run(); }
    public void start() { thread.start(); }
    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        inbox.stopAccepting();
        LockSupport.unpark(thread);
        try {
            while (thread.isAlive()) {
                LockSupport.unpark(thread);
                thread.join(1000);
            }
        } catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
        inbox.discardQueued();
        publications.close();
        uploads.close();
    }
    public void rootCallbacks(IntConsumer added, IntConsumer removed) { render.rootCallbacks(added, removed); }
    public void tick(DeviceBuffer nodes, VisibilityEviction cleaner) { render.tick(nodes, cleaner); }
    public void request(NativeBuffer batch) { inbox.request(batch); }
    public void removal(NativeBuffer batch) { inbox.removal(batch); }
    public void addRoot(long position) { inbox.addRoot(position); }
    public void removeRoot(long position) { inbox.removeRoot(position); }
    public boolean hasWork() { return inbox.work.get() != 0 || publications.hasPublished(); }
    public int lastNode() { return render.lastNode; }
    public long usedBytes() { return render.usedBytes; }
    public long geometryCapacity() { return geometryCapacity; }
    public void addDebug(List<String> debug) {
        debug.add("TerraVista geometry used/capacity MiB,nodes: " + (usedBytes() / (1 << 20)) + "/" + (geometryCapacity / (1 << 20)) + "," + storage.size());
    }
    public void worldChanged(ResidentSection section, int flags, int neighborMask) {
        subscriptions.forward(section, flags);
        if (ClientOptions.CONFIG.biomeBlendRadius > 0 && (flags & VoxelWorld.BLOCKS_CHANGED) != 0) {
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dz != 0) neighbor(section, dx, 0, dz);
            neighborMask &= 0b000011;
        }
        if (neighborMask != 0) {
            if ((neighborMask & 0b000001) != 0) neighbor(section, 0, -1, 0);
            if ((neighborMask & 0b000010) != 0) neighbor(section, 0, 1, 0);
            if ((neighborMask & 0b000100) != 0) neighbor(section, -1, 0, 0);
            if ((neighborMask & 0b001000) != 0) neighbor(section, 1, 0, 0);
            if ((neighborMask & 0b010000) != 0) neighbor(section, 0, 0, -1);
            if ((neighborMask & 0b100000) != 0) neighbor(section, 0, 0, 1);
        }
    }
    private void neighbor(ResidentSection section, int dx, int dy, int dz) {
        var contents = section.contents();
        subscriptions.requestRemesh(SectionCoordinates.key(contents.level, contents.x + dx, contents.y + dy, contents.z + dz));
    }
}

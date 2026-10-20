package dev.terravista.render.tree;

import dev.terravista.memory.IndexedObjects;
import dev.terravista.mesh.SectionMesh;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;

/** 持有 CPU 树状态，由工作线程中的请求、变更与驱逐服务修改。 */
public final class RenderTree {
    static final int NULL_GEOMETRY = -1, EMPTY_GEOMETRY = -2;
    static final int NULL_REQUEST = NodeRecords.REQUEST_MASK;
    static final int EMPTY_CHILDREN = NodeRecords.NODE_MASK - 1;
    static final int ID_MASK = (1 << 24) - 1;
    static final int TYPE_MASK = 3 << 30, LEAF = 0, INNER = 1 << 30, REQUEST = 2 << 30;
    static final int ROOT_REQUEST = 0, CHILD_REQUEST = 1 << 29, REQUEST_MASK = 1 << 29;

    final IndexedObjects<RootMeshRequest> rootRequests = new IndexedObjects<>(RootMeshRequest[]::new, NodeRecords.REQUEST_MASK);
    final IndexedObjects<ChildMeshRequest> childRequests = new IndexedObjects<>(ChildMeshRequest[]::new, NodeRecords.REQUEST_MASK);
    final IntOpenHashSet dirty = new IntOpenHashSet();
    final SectionGeometryAllocator allocator;
    final SectionWatch watch;
    final Long2IntOpenHashMap active = new Long2IntOpenHashMap();
    final NodeRecords nodes;
    public final int maxNodes;
    final IntOpenHashSet rootIds = new IntOpenHashSet();
    final LongOpenHashSet roots = new LongOpenHashSet();
    int childRequestCount;
    IntConsumer rootAdded, rootRemoved;
    private Cleaner cleaner;
    final NodeGeometry geometry;
    final NodeRequests requests;
    final NodeEviction eviction;
    final NodeTransitions transitions;

    public interface Cleaner {
        void allocated(int id);
        void moved(int from, int to);
        void freed(int id);
    }
    public RenderTree(int maxNodes, SectionGeometryAllocator allocator, SectionWatch watch) {
        if ((maxNodes & (maxNodes - 1)) != 0) throw new IllegalArgumentException("TerraVista Render: Max node count must be a power of 2");
        if (maxNodes > (1 << 24)) throw new IllegalArgumentException("TerraVista Render: Max node count cannot exceed 2^24");
        active.defaultReturnValue(-1);
        this.watch = watch;
        this.maxNodes = maxNodes;
        nodes = new NodeRecords(maxNodes);
        this.allocator = allocator;
        geometry = new NodeGeometry(allocator, nodes);
        requests = new NodeRequests(this);
        eviction = new NodeEviction(this);
        transitions = new NodeTransitions(this);
    }
    public void cleaner(Cleaner cleaner) { this.cleaner = cleaner; }
    void allocated(int id) { if (cleaner != null) cleaner.allocated(id); }
    void moved(int from, int to) { if (cleaner != null) cleaner.moved(from, to); }
    void freed(int id) { if (cleaner != null) cleaner.freed(id); }
    public void rootCallbacks(IntConsumer added, IntConsumer removed) { rootAdded = added; rootRemoved = removed; }
    public void insertRoot(long position) { requests.beginRoot(position); }
    public void request(long position) { requests.request(position); }
    public void geometryResult(SectionMesh mesh) { requests.geometryResult(mesh); }
    public void removeRoot(long position) { eviction.removeRoot(position); }
    public void removeGeometry(long position) { eviction.removeGeometry(position); }
    public void childrenChanged(long position, byte children) { transitions.childrenChanged(position, children); }
    IntOpenHashSet dirtyNodes() { return dirty; }
    void writeNode(int node, long address) { nodes.write(address, node); }
    IntOpenHashSet rootIds() { return rootIds; }
    public void addDebug(List<String> debug) { debug.add("TerraVista tree nodes/requests: " + active.size() + "/" + (rootRequests.size() + childRequests.size())); }
    public int lastNode() { return nodes.reportedEnd(); }
}

package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import static dev.terravista.render.tree.RenderTree.*;

/** 将世界子掩码变化应用到待处理请求与紧凑驻留子节点。 */
final class NodeTransitions {
    private final RenderTree tree;
    NodeTransitions(RenderTree tree) { this.tree = tree; }

    void childrenChanged(long position, byte children) {
        int encoded = tree.active.get(position);
        if (encoded == -1) {
            ModLog.warn("Render: Got child change for pos " + SectionCoordinates.describe(position) + " but it was not in active map, ignoring!");
            return;
        }
        int type = encoded & TYPE_MASK, node = encoded & ID_MASK;
        if (type == REQUEST) {
            if ((encoded & REQUEST_MASK) == ROOT_REQUEST) tree.requests.rootChildren(node, children);
            else if ((encoded & REQUEST_MASK) == CHILD_REQUEST) tree.requests.childChildren(node, position, children);
            else throw new IllegalStateException();
        } else if (type == INNER) updateInner(position, node, children);
        else if (type == LEAF) updateLeaf(position, node, children);
    }
    private void updateLeaf(long position, int node, byte children) {
        if (tree.nodes.requestInFlight(node)) {
            int requestId = tree.nodes.request(node);
            var request = tree.childRequests.get(requestId);
            if (request.position() != position)
                throw new IllegalStateException("TerraVista Render: Request is not at pos, got " + SectionCoordinates.describe(request.position()) + " expected " + SectionCoordinates.describe(position));
            byte previous = request.mask();
            byte change = (byte) (previous ^ children);
            int removed = change & previous;
            for (int child = 0; child < 8; child++) {
                if ((removed & (1 << child)) == 0) continue;
                long at = TreeCoordinates.child(position, child);
                releaseResult(request, child);
                if (tree.active.remove(at) == -1)
                    throw new IllegalStateException("TerraVista Render: Child pos was in a request but not in active section map");
                if (!tree.watch.unwatch(at, VoxelWorld.DEFAULT_CHANGES))
                    throw new IllegalStateException("TerraVista Render: Child pos was not being watched");
            }
            addRequirements(position, requestId, request, change & children);
            if (request.satisfied()) tree.requests.finishChild(requestId, request);
        }
        // 先按当前父掩码完成处理，再发布世界掩码。
        tree.nodes.childMask(node, children);
        tree.dirty.add(node);
    }
    private void releaseResult(ChildMeshRequest request, int child) {
        int geometry = request.remove(child);
        if (geometry != NULL_GEOMETRY && geometry != EMPTY_GEOMETRY) tree.allocator.remove(geometry);
    }
    private void addRequirements(long position, int requestId, ChildMeshRequest request, int added) {
        for (int child = 0; child < 8; child++) {
            if ((added & (1 << child)) == 0) continue;
            request.require(child);
            long at = TreeCoordinates.child(position, child);
            if (tree.active.put(at, requestId | REQUEST | CHILD_REQUEST) != -1)
                throw new IllegalStateException("TerraVista Render: Child pos was already in active section tracker but was part of a request");
            if (!tree.watch.watch(at, VoxelWorld.DEFAULT_CHANGES))
                throw new IllegalStateException("TerraVista Render: Child pos update router issue");
        }
    }
    private void updateInner(long position, int node, byte children) {
        if (children == 0) ModLog.warn("Render: Internal node now has an empty child mask");
        byte previous = tree.nodes.childMask(node);
        byte added = (byte) ((previous ^ children) & children);
        if (added != 0) {
            if (!tree.nodes.requestInFlight(node)) {
                int id = tree.childRequests.add(new ChildMeshRequest(position));
                tree.nodes.requestInFlight(node, true);
                tree.nodes.request(node, id);
                tree.childRequestCount++;
            }
            int requestId = tree.nodes.request(node);
            var request = tree.childRequests.get(requestId);
            if (request.position() != position)
                throw new IllegalStateException("TerraVista Render: Request is not at pos: got " + SectionCoordinates.describe(position) + " expected: " + SectionCoordinates.describe(request.position()));
            addRequirements(position, requestId, request, added);
        }
        tree.nodes.childMask(node, children);
        int removed = ((previous ^ children) & previous) & 0xFF;
        if (removed != 0) {
            removed = removePending(position, node, removed);
            if (removed != 0) compact(position, node, children, removed);
            if (tree.nodes.requestInFlight(node)) {
                int id = tree.nodes.request(node);
                var request = tree.childRequests.get(id);
                if (request.position() != position) throw new IllegalStateException("TerraVista Render: Request is not at pos");
                if (request.satisfied()) tree.requests.finishChild(id, request);
            }
        }
        if (children == 0) becomeLeaf(position, node);
    }
    private int removePending(long position, int node, int removed) {
        if (tree.nodes.requestInFlight(node)) {
            int id = tree.nodes.request(node);
            var request = tree.childRequests.get(id);
            if (request.position() != position) throw new IllegalStateException("TerraVista Render: Request is not at pos");
            int pending = Byte.toUnsignedInt(request.mask()) & removed;
            for (int child = 0; child < 8; child++) {
                if ((pending & (1 << child)) == 0) continue;
                long at = TreeCoordinates.child(position, child);
                releaseResult(request, child);
                int encoded = tree.active.remove(at);
                if (encoded == -1 || (encoded & TYPE_MASK) != REQUEST)
                    throw new IllegalStateException("TerraVista Render: Child pos was in a request but not in active section map");
                if (!tree.watch.unwatch(at, VoxelWorld.DEFAULT_CHANGES))
                    throw new IllegalStateException("TerraVista Render: Child pos was not being watched");
            }
            removed ^= pending;
        }
        return removed;
    }
    private void compact(long position, int node, byte children, int removed) {
        int base = tree.nodes.childBase(node), count = tree.nodes.childCount(node);
        if (base == -1) throw new IllegalStateException();
        int existing = 0;
        for (int i = 0; i < count; i++) {
            if (!tree.nodes.contains(base + i)) throw new IllegalStateException();
            existing |= 1 << TreeCoordinates.childIndex(tree.nodes.position(base + i));
        }
        if ((removed & existing) != removed) throw new IllegalStateException();
        int retained = removed ^ existing;
        if (retained == 0) {
            if (children != 0 && !tree.nodes.requestInFlight(node)) throw new IllegalStateException();
            tree.nodes.childrenAreLeaves(node, false);
            tree.nodes.childBase(node, EMPTY_CHILDREN);
            tree.nodes.childCount(node, 8);
            for (int child = 0; child < 8; child++)
                if ((removed & (1 << child)) != 0) tree.eviction.remove(TreeCoordinates.child(position, child));
        } else {
            int nextCount = Integer.bitCount(retained), nextBase = tree.nodes.allocate(nextCount);
            int source = base - 1, target = nextBase - 1;
            boolean allLeaves = true;
            for (int child = 0; child < 8; child++) {
                if ((existing & (1 << child)) == 0) continue;
                source++;
                if ((removed & (1 << child)) != 0) tree.eviction.remove(TreeCoordinates.child(position, child));
                else {
                    target++;
                    long at = tree.nodes.position(source);
                    if (at != TreeCoordinates.child(position, child)) throw new IllegalStateException();
                    tree.nodes.copy(source, target);
                    tree.allocated(target);
                    tree.moved(source, target);
                    tree.freed(source);
                    int encoded = tree.active.get(at);
                    if ((encoded & TYPE_MASK) == REQUEST) throw new IllegalStateException();
                    if ((encoded & ID_MASK) != source) throw new IllegalStateException("TerraVista Render: State inconsistency");
                    allLeaves &= (encoded & TYPE_MASK) == LEAF;
                    tree.active.put(at, (encoded & TYPE_MASK) | target);
                    tree.nodes.release(source);
                    tree.dirty.add(source);
                    tree.dirty.add(target);
                }
            }
            tree.nodes.childrenAreLeaves(node, allLeaves);
            tree.nodes.childBase(node, nextBase);
            tree.nodes.childCount(node, nextCount);
            for (int i = 0; i < count; i++) if (tree.nodes.contains(base + i)) throw new IllegalStateException();
        }
        tree.dirty.add(node);
    }
    private void becomeLeaf(long position, int node) {
        if (tree.nodes.requestInFlight(node)) throw new IllegalStateException();
        if (tree.nodes.geometry(node) == NULL_GEOMETRY) {
            ModLog.error("Render: Transforming inner node to leaf node while it has null geometry");
            if (!tree.nodes.geometryInFlight(node)) {
                if ((tree.watch.get(position) & VoxelWorld.BLOCKS_CHANGED) != 0)
                    throw new IllegalStateException("TerraVista Render: Watcher was already watching for geometry update, but geometry was null");
                tree.requests.request(position);
                if ((tree.watch.get(position) & VoxelWorld.BLOCKS_CHANGED) == 0 || !tree.nodes.geometryInFlight(node))
                    throw new IllegalStateException("TerraVista Render: Watcher must be watching for geometry update");
            }
            ModLog.error("Render: Setting geometry to EMPTY while request is inflight");
            tree.nodes.geometry(node, EMPTY_GEOMETRY);
        }
        if (tree.nodes.childBase(node) != EMPTY_CHILDREN) throw new IllegalStateException();
        tree.nodes.childBase(node, -1);
        tree.active.put(position, LEAF | node);
        tree.nodes.childrenAreLeaves(node, false);
        tree.dirty.add(node);
    }
}

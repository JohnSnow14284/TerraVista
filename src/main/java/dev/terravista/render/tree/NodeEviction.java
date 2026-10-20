package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import static dev.terravista.render.tree.RenderTree.*;

/** 递归移除驻留节点，清理请求并在几何压力下折叠子树。 */
final class NodeEviction {
    private final RenderTree tree;
    NodeEviction(RenderTree tree) { this.tree = tree; }

    void removeRoot(long position) {
        if (!tree.roots.remove(position))
            throw new IllegalStateException("TerraVista Render: Position not in top level map: " + SectionCoordinates.describe(position));
        int encoded = tree.active.get(position);
        if (encoded == -1)
            throw new IllegalStateException("TerraVista Render: Tried removing top level pos " + SectionCoordinates.describe(position) + " but it was not in active map, discarding!");
        if ((encoded & TYPE_MASK) != REQUEST) {
            int node = encoded & ID_MASK;
            if (!tree.rootIds.remove(node))
                throw new IllegalStateException("TerraVista Render: Node id was not in top level node ids: " + encoded + " pos: " + SectionCoordinates.describe(position));
            if (tree.rootRemoved != null) tree.rootRemoved.accept(node);
        }
        remove(position);
    }
    private void removeRequest(int requestId, ChildMeshRequest request, long position) {
        for (int child = 0; child < 8; child++) {
            if ((request.mask() & (1 << child)) == 0) continue;
            long at = TreeCoordinates.child(position, child);
            int mesh = request.mesh(child);
            if (mesh != EMPTY_GEOMETRY && mesh != NULL_GEOMETRY) tree.allocator.remove(mesh);
            int encoded = tree.active.remove(at);
            if (encoded == -1) throw new IllegalStateException("TerraVista Render: Child not in activeMap");
            if ((encoded & TYPE_MASK) != REQUEST || (encoded & REQUEST_MASK) != CHILD_REQUEST || (encoded & ID_MASK) != requestId)
                throw new IllegalStateException("TerraVista Render: Invalid child active state map: " + encoded);
            if (!tree.watch.unwatch(at, VoxelWorld.DEFAULT_CHANGES)) throw new IllegalStateException("TerraVista Render: Pos was not being watched");
        }
        tree.childRequests.release(requestId);
        tree.childRequestCount--;
    }
    void remove(long position) { remove(position, false); }
    void removeChildren(long position) { remove(position, true); }
    private void remove(long position, boolean childrenOnly) {
        int encoded = childrenOnly ? tree.active.get(position) : tree.active.remove(position);
        if (encoded == -1) throw new IllegalStateException("TerraVista Render: Cannot remove pos that doesnt exist");
        int type = encoded & TYPE_MASK;
        if (type == INNER || type == LEAF) {
            int node = encoded & ID_MASK;
            if (!tree.nodes.contains(node)) throw new IllegalStateException("TerraVista Render: Node exists in section map but not in nodeData");
            byte children = tree.nodes.childMask(node);
            if (tree.nodes.requestInFlight(node)) {
                int requestId = tree.nodes.request(node);
                var request = tree.childRequests.get(requestId);
                children ^= request.mask();
                removeRequest(requestId, request, position);
                if (childrenOnly) {
                    tree.nodes.requestInFlight(node, false);
                    tree.nodes.request(node, NULL_REQUEST);
                }
            }
            if (type == INNER) {
                byte mask = 0;
                int base = tree.nodes.childBase(node);
                if (base == -1) throw new IllegalStateException();
                if (base != EMPTY_CHILDREN) {
                    int count = tree.nodes.childCount(node);
                    if (Integer.bitCount(Byte.toUnsignedInt(children)) != count) throw new IllegalStateException();
                    for (int i = 0; i < count; i++) {
                        if (!tree.nodes.contains(i + base)) throw new IllegalStateException();
                        long at = tree.nodes.position(i + base);
                        if (TreeCoordinates.parent(at) != position) throw new IllegalStateException();
                        mask |= (byte) (1 << TreeCoordinates.childIndex(at));
                    }
                }
                if (mask != children) throw new IllegalStateException();
                for (int child = 0; child < 8; child++) {
                    if ((children & (1 << child)) == 0) continue;
                    remove(TreeCoordinates.child(position, child));
                }
                base = tree.nodes.childBase(node);
                if (base == -1) throw new IllegalStateException();
                if (base != EMPTY_CHILDREN) {
                    int count = tree.nodes.childCount(node);
                    if (Integer.bitCount(Byte.toUnsignedInt(children)) != count) throw new IllegalStateException();
                    for (int i = 0; i < count; i++) if (tree.nodes.contains(i + base)) throw new IllegalStateException();
                }
                if (childrenOnly) tree.nodes.childBase(node, -1);
            }
            if (!childrenOnly) {
                int mesh = tree.nodes.geometry(node);
                if (mesh != EMPTY_GEOMETRY && mesh != NULL_GEOMETRY) tree.allocator.remove(mesh);
                tree.nodes.release(node);
                tree.freed(node);
                tree.dirty.add(node);
                if (!tree.watch.unwatch(position, VoxelWorld.DEFAULT_CHANGES)) throw new IllegalStateException("TerraVista Render: Pos was not being watched");
            } else {
                tree.nodes.childrenAreLeaves(node, false);
                tree.dirty.add(node);
            }
        } else if (type == REQUEST) {
            if (!tree.watch.unwatch(position, VoxelWorld.DEFAULT_CHANGES)) throw new IllegalStateException("TerraVista Render: Pos was not being watched");
            if ((encoded & REQUEST_MASK) == ROOT_REQUEST) {
                int id = encoded & ID_MASK;
                var request = tree.rootRequests.get(id);
                if (request.position() != position) throw new IllegalStateException();
                tree.rootRequests.release(id);
                if (request.hasMesh()) {
                    int mesh = request.mesh();
                    if (mesh != EMPTY_GEOMETRY && mesh != NULL_GEOMETRY) tree.allocator.remove(mesh);
                }
            } else {
                int id = encoded & ID_MASK;
                var request = tree.childRequests.get(id);
                if (request.position() != position) throw new IllegalStateException();
                removeRequest(id, request, position);
            }
        } else throw new IllegalStateException();
    }
    void removeGeometry(long position) {
        int encoded = tree.active.get(position);
        if (encoded == -1) return;
        int type = encoded & TYPE_MASK, node = encoded & ID_MASK;
        if (type == REQUEST) return;
        if (type == INNER) clearGeometry(position, node);
        else {
            if (tree.roots.contains(position)) {
                int geometry = tree.nodes.geometry(node);
                if (geometry != NULL_GEOMETRY && geometry != EMPTY_GEOMETRY) {
                    ModLog.warn("Render: Tried removing geometry from top level node which is not allowed, disregarding request");
                    return;
                }
            } else collapseParent(position);
        }
    }
    private void collapseParent(long child) {
        long position = TreeCoordinates.parent(child);
        int encoded = tree.active.get(position);
        if (encoded == -1) throw new IllegalStateException("TerraVista Render: Parent node must exist");
        if ((encoded & TYPE_MASK) != INNER) throw new IllegalStateException("TerraVista Render: Parent node must be an inner node");
        int parent = encoded & ID_MASK;
        if (tree.nodes.geometry(parent) == NULL_GEOMETRY) tree.requests.request(position);
        else {
            removeChildren(position);
            int previous = tree.active.put(position, LEAF | parent);
            if (previous == -1) throw new IllegalStateException();
            if ((previous & TYPE_MASK) != INNER || (previous & ID_MASK) != parent) throw new IllegalStateException();
            tree.nodes.childrenAreLeaves(parent, false);
        }
    }
    private void clearGeometry(long position, int node) {
        int mesh = tree.nodes.geometry(node);
        if (mesh != NULL_GEOMETRY && mesh != EMPTY_GEOMETRY) {
            if (tree.watch.unwatch(position, VoxelWorld.BLOCKS_CHANGED))
                throw new IllegalStateException("TerraVista Render: Unwatching position for geometry removal at: " + SectionCoordinates.describe(position) + " resulted in full removal");
            tree.allocator.remove(mesh);
            tree.nodes.geometry(node, NULL_GEOMETRY);
            tree.dirty.add(node);
            tree.nodes.geometryInFlight(node, false);
        }
    }
}

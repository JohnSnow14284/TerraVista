package dev.terravista.render.tree;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.mesh.SectionMesh;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import static dev.terravista.render.tree.RenderTree.*;

/** 接收请求结果并发布到 CPU 节点记录。 */
final class NodeRequests {
    private final RenderTree tree;
    private int repeatWarnings = 1;
    NodeRequests(RenderTree tree) { this.tree = tree; }

    void beginRoot(long position) {
        TreeCoordinates.validate(position);
        if (tree.active.containsKey(position)) {
            ModLog.error("Render: Tried inserting top level pos " + SectionCoordinates.describe(position) + " but it was in active map, discarding!");
            return;
        }
        var request = new RootMeshRequest(position);
        int id = tree.rootRequests.add(request);
        tree.watch.watch(position, VoxelWorld.DEFAULT_CHANGES);
        tree.active.put(position, id | REQUEST | ROOT_REQUEST);
        tree.roots.add(position);
    }
    void rootGeometry(int requestId, SectionMesh mesh) {
        var request = tree.rootRequests.get(requestId);
        request.mesh(tree.geometry.replaceResult(request.mesh(), mesh));
        if (!request.hasChildren()) request.children(mesh.children);
        if (request.satisfied()) {
            tree.rootRequests.release(requestId);
            finishRoot(request);
        }
    }
    void rootChildren(int requestId, byte children) {
        var request = tree.rootRequests.get(requestId);
        request.children(children);
        if (request.satisfied()) {
            tree.rootRequests.release(requestId);
            finishRoot(request);
        }
    }
    private void finishRoot(RootMeshRequest request) {
        int node = tree.nodes.allocate();
        tree.nodes.position(node, request.position());
        tree.nodes.geometry(node, request.mesh());
        tree.nodes.childMask(node, request.children());
        tree.active.put(request.position(), node | LEAF);
        tree.dirty.add(node);
        if (!tree.rootIds.add(node)) throw new IllegalStateException();
        tree.allocated(node);
        if (tree.rootAdded != null) tree.rootAdded.accept(node);
    }
    void geometryResult(SectionMesh mesh) {
        long position = mesh.position;
        int node = tree.active.get(position);
        if (node == -1) { mesh.free(); return; }
        int type = node & TYPE_MASK;
        if (type == REQUEST) {
            if ((node & REQUEST_MASK) == ROOT_REQUEST) rootGeometry(node & ID_MASK, mesh);
            else if ((node & REQUEST_MASK) == CHILD_REQUEST) {
                int requestId = node & ID_MASK;
                var request = tree.childRequests.get(requestId);
                int child = TreeCoordinates.childIndex(position);
                request.mesh(child, tree.geometry.replaceResult(request.mesh(child), mesh));
                if (!request.hasDescendants(child)) request.descendants(child, mesh.children);
                if (request.satisfied()) finishChild(requestId, request);
            } else throw new IllegalStateException();
        } else if (type == INNER || type == LEAF) {
            node &= ID_MASK;
            if ((tree.watch.get(position) & VoxelWorld.BLOCKS_CHANGED) == 0) {
                if (tree.nodes.geometryInFlight(node)) throw new IllegalStateException();
                ModLog.warn("Render: Geometry update has no subscription; discarding it");
                mesh.free();
                return;
            }
            tree.nodes.geometryInFlight(node, false);
            if (tree.geometry.update(node, mesh) != 0) tree.dirty.add(node);
        } else throw new IllegalStateException();
    }
    void childChildren(int requestId, long position, byte children) {
        var request = tree.childRequests.get(requestId);
        request.descendants(TreeCoordinates.childIndex(position), children);
        if (request.satisfied()) finishChild(requestId, request);
    }
    void request(long position) {
        int encoded = tree.active.get(position);
        if (encoded == -1) return;
        int type = encoded & TYPE_MASK;
        int node = encoded & ID_MASK;
        if (type == REQUEST) {
            ModLog.error("Render: Tried processing request for pos: " + SectionCoordinates.describe(position) + " but its type was a request, ignoring!");
            return;
        }
        if (type != LEAF && type != INNER) throw new IllegalStateException("TerraVista Render: Unknown node type: " + type);
        if (SectionCoordinates.level(position) == 0) {
            ModLog.error("Render: Requests cannot exist for bottom level nodes. at: " + SectionCoordinates.describe(position) + ". Ignoring request");
            return;
        }
        if (type == LEAF) {
            if (tree.nodes.geometry(node) == NULL_GEOMETRY) {
                ModLog.warn("Render: Got request for leaf that doesnt have geometry, this should not be possible at pos " + SectionCoordinates.describe(position));
                if (!tree.watch.watch(position, VoxelWorld.BLOCKS_CHANGED))
                    ModLog.warn("Render: Node: " + node + " at pos: " + SectionCoordinates.describe(position) + " got update request, but geometry was already being watched");
                return;
            }
            if (tree.nodes.requestInFlight(node)) {
                if (repeatWarnings >= 1 && repeatWarnings < 100) {
                    ModLog.warn("Render: Tried processing a node that already has a request in flight: " + node + " pos: " + SectionCoordinates.describe(position) + " ignoring");
                    repeatWarnings++;
                } else if (repeatWarnings == 100) {
                    ModLog.warn("Render: Further duplicate node-request warnings are suppressed");
                    repeatWarnings = 0;
                }
                return;
            }
            tree.nodes.requestInFlight(node, true);
            expandLeaf(node);
        } else requestInnerGeometry(position, node);
    }
    private void expandLeaf(int node) {
        long position = tree.nodes.position(node);
        byte children = tree.nodes.childMask(node);
        if (children == 0 && !tree.roots.contains(position)) {
            ModLog.warn("Render: Not creating a leaf request with existence mask of 0 at pos", SectionCoordinates.describe(position));
            tree.nodes.requestInFlight(node, false);
            tree.dirty.add(node);
            return;
        }
        var request = new ChildMeshRequest(position);
        int requestId = tree.childRequests.add(request);
        for (int child = 0; child < 8; child++) {
            if ((children & (1 << child)) == 0) continue;
            long childPosition = TreeCoordinates.child(position, child);
            request.require(child);
            int previous = tree.active.put(childPosition, requestId | REQUEST | CHILD_REQUEST);
            if (previous != -1) {
                String extra = "";
                if ((previous & TYPE_MASK) == LEAF)
                    extra = " type leaf: pos " + SectionCoordinates.describe(tree.nodes.position(previous))
                            + " hasRequest: " + tree.nodes.requestInFlight(previous);
                throw new IllegalStateException("TerraVista Render: Leaf request creation failed to insert child into map as a mapping already existed for the node! pos: "
                        + SectionCoordinates.describe(childPosition) + " id: " + previous + " for parent " + SectionCoordinates.describe(position) + " extra " + extra);
            }
            if (!tree.watch.watch(childPosition, VoxelWorld.DEFAULT_CHANGES)) throw new IllegalStateException("TerraVista Render: Failed to watch childPos");
        }
        tree.nodes.request(node, requestId);
        tree.childRequestCount++;
    }
    private void requestInnerGeometry(long position, int node) {
        int geometry = tree.nodes.geometry(node);
        boolean watched = (tree.watch.get(position) & VoxelWorld.BLOCKS_CHANGED) != 0;
        boolean inFlight = tree.nodes.geometryInFlight(node);
        if (inFlight && !watched) throw new IllegalStateException();
        if (geometry != NULL_GEOMETRY && inFlight && geometry != EMPTY_GEOMETRY) throw new IllegalStateException();
        if (!tree.nodes.geometryInFlight(node)) {
            if (!tree.watch.watch(position, VoxelWorld.BLOCKS_CHANGED)) tree.dirty.add(node);
            else tree.nodes.geometryInFlight(node, true);
        }
    }
    void finishChild(int requestId, ChildMeshRequest request) {
        int parent = tree.active.get(request.position());
        if (parent == -1 || (parent & TYPE_MASK) == REQUEST)
            throw new IllegalStateException("TerraVista Render: Child request completed after its parent was removed or replaced: "
                    + SectionCoordinates.describe(request.position()) + " " + parent);
        int type = parent & TYPE_MASK;
        parent &= ID_MASK;
        if (request.mask() == 0) {
            tree.childRequests.release(requestId);
            tree.nodes.request(parent, NULL_REQUEST);
            tree.nodes.requestInFlight(parent, false);
            tree.childRequestCount--;
            tree.dirty.add(parent);
            return;
        }
        if (type == LEAF) installLeafChildren(parent, requestId, request);
        else if (type == INNER) mergeChildren(parent, requestId, request);
        else throw new IllegalStateException();
    }
    private void installLeafChildren(int parent, int requestId, ChildMeshRequest request) {
        int mask = Byte.toUnsignedInt(request.mask());
        if (mask == 0) throw new IllegalStateException();
        int base = tree.nodes.allocate(Integer.bitCount(mask));
        int offset = -1;
        for (int child = 0; child < 8; child++) {
            if ((mask & (1 << child)) == 0) continue;
            offset++;
            long position = TreeCoordinates.child(request.position(), child);
            int node = base + offset;
            tree.nodes.position(node, position);
            byte children = request.descendants(child);
            if (children == 0) ModLog.warn("Render: Request result with child existence of 0, for child pos " + SectionCoordinates.describe(position));
            tree.nodes.childMask(node, children);
            tree.nodes.geometry(node, request.mesh(child));
            tree.dirty.add(node);
            int previous = tree.active.put(position, node | LEAF);
            if ((previous & TYPE_MASK) != REQUEST)
                throw new IllegalStateException("TerraVista Render: Put node in map from request but type was not request: " + previous + " " + SectionCoordinates.describe(position));
            tree.allocated(node);
        }
        tree.childRequests.release(requestId);
        tree.nodes.childBase(parent, base);
        tree.nodes.childCount(parent, Integer.bitCount(mask));
        tree.nodes.request(parent, NULL_REQUEST);
        tree.childRequestCount--;
        tree.nodes.requestInFlight(parent, false);
        if ((tree.active.put(request.position(), INNER | parent) & TYPE_MASK) != LEAF) throw new IllegalStateException();
        tree.dirty.add(parent);
        tree.nodes.childrenAreLeaves(parent, true);
        if (!tree.roots.contains(request.position())) {
            int grandparent = tree.active.get(TreeCoordinates.parent(request.position()));
            if ((grandparent & TYPE_MASK) != INNER) throw new IllegalStateException();
            tree.nodes.childrenAreLeaves(grandparent & ID_MASK, false);
        }
    }
    private void mergeChildren(int parent, int requestId, ChildMeshRequest request) {
        int oldBase = tree.nodes.childBase(parent), oldCount = tree.nodes.childCount(parent);
        if (oldBase == -1) throw new IllegalStateException();
        int existing = 0;
        if (oldBase != EMPTY_CHILDREN) {
            for (int i = 0; i < oldCount; i++) {
                if (!tree.nodes.contains(i + oldBase)) throw new IllegalStateException();
                existing |= 1 << TreeCoordinates.childIndex(tree.nodes.position(i + oldBase));
            }
        }
        int incoming = Byte.toUnsignedInt(request.mask());
        if ((byte) (existing | incoming) != tree.nodes.childMask(parent))
            throw new IllegalStateException("TerraVista Render: node data existence state does not match pointer mask");
        if ((incoming & existing) != 0) throw new IllegalStateException("TerraVista Render: Child request overlaps existing child data");
        int combined = incoming | existing;
        int base = tree.nodes.allocate(Integer.bitCount(combined));
        int node = base - 1, previousChild = oldBase - 1;
        for (int child = 0; child < 8; child++) {
            if ((combined & (1 << child)) == 0) continue;
            node++;
            if ((incoming & (1 << child)) != 0) {
                long position = TreeCoordinates.child(request.position(), child);
                tree.nodes.position(node, position);
                byte children = request.descendants(child);
                tree.nodes.childMask(node, children);
                tree.nodes.geometry(node, request.mesh(child));
                tree.dirty.add(node);
                int previous = tree.active.put(position, node | LEAF);
                if ((previous & TYPE_MASK) != REQUEST)
                    throw new IllegalStateException("TerraVista Render: Put node in map from request but type was not request: " + previous + " " + SectionCoordinates.describe(position));
                tree.allocated(node);
            } else {
                previousChild++;
                long position = tree.nodes.position(previousChild);
                tree.nodes.copy(previousChild, node);
                tree.allocated(node);
                tree.moved(previousChild, node);
                tree.freed(previousChild);
                int previous = tree.active.get(position);
                if ((previous & TYPE_MASK) == REQUEST) throw new IllegalStateException();
                if ((previous & ID_MASK) != previousChild) throw new IllegalStateException("TerraVista Render: State inconsistency");
                tree.active.put(position, (previous & TYPE_MASK) | node);
                tree.dirty.add(previousChild);
                tree.dirty.add(node);
            }
        }
        if (oldBase != EMPTY_CHILDREN) tree.nodes.release(oldBase, oldCount);
        if (oldBase == EMPTY_CHILDREN) tree.nodes.childrenAreLeaves(parent, true);
        tree.childRequests.release(requestId);
        tree.nodes.childBase(parent, base);
        tree.nodes.childCount(parent, Integer.bitCount(combined));
        tree.nodes.request(parent, NULL_REQUEST);
        tree.childRequestCount--;
        tree.nodes.requestInFlight(parent, false);
        tree.dirty.add(parent);
    }
}

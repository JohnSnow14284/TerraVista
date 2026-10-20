package dev.terravista.render.tree;

import dev.terravista.mesh.SectionMesh;

/** 协调根、子请求与节点变化中的几何所有权和编号。 */
final class NodeGeometry {
    private static final int NULL = -1, EMPTY = -2;
    private final SectionGeometryAllocator geometry;
    private final NodeRecords nodes;

    NodeGeometry(SectionGeometryAllocator geometry, NodeRecords nodes) {
        this.geometry = geometry; this.nodes = nodes;
    }
    int replaceResult(int previous, SectionMesh mesh) {
        if (mesh.isEmpty()) {
            if (previous != NULL && previous != EMPTY) geometry.remove(previous);
            mesh.free();
            return EMPTY;
        }
        if (previous != NULL && previous != EMPTY) return geometry.replace(previous, mesh);
        return geometry.upload(mesh);
    }
    int update(int node, SectionMesh mesh) {
        int previous = nodes.geometry(node);
        int next = EMPTY;
        if (previous != EMPTY && previous != NULL) {
            if (!mesh.isEmpty()) next = geometry.replace(previous, mesh);
            else geometry.remove(previous);
        } else if (!mesh.isEmpty()) next = geometry.upload(mesh);
        if (previous != next) nodes.geometry(node, next);
        if (previous == next) return 0;
        if (previous == EMPTY || previous == NULL) return 1;
        return 2;
    }
}

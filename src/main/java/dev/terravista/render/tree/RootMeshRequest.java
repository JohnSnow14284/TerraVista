package dev.terravista.render.tree;

/** 根请求在网格与子节点存在结果均到达后就绪。 */
final class RootMeshRequest {
    private final long position;
    private int mesh = -1;
    private byte children;
    private int received;

    RootMeshRequest(long position) { this.position = position; }
    void children(byte mask) { received |= 2; children = mask; }
    int mesh(int result) {
        received |= 1;
        int previous = mesh;
        mesh = result;
        return previous;
    }
    boolean satisfied() { return received == 3; }
    long position() { return position; }
    int mesh() { return mesh; }
    byte children() { return children; }
    boolean hasChildren() { return (received & 2) != 0; }
    boolean hasMesh() { return (received & 1) != 0; }
}

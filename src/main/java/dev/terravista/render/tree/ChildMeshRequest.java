package dev.terravista.render.tree;

/** 分别记录所需子节点与已收到网格和存在结果的掩码。 */
final class ChildMeshRequest {
    private final long position;
    private final int[] meshes = {-1, -1, -1, -1, -1, -1, -1, -1};
    private final byte[] descendants = new byte[8];
    private byte received;
    private byte required;
    private byte existenceReceived;

    ChildMeshRequest(long position) { this.position = position; }
    int mesh(int child) {
        if ((required & (1 << child)) == 0)
            throw new IllegalStateException("TerraVista Render: Tried getting mesh result of child not in mask");
        return meshes[child];
    }
    void descendants(int child, byte mask) {
        if ((required & (1 << child)) == 0)
            throw new IllegalStateException("TerraVista Render: Tried setting child child existence in request when child isnt in mask");
        descendants[child] = mask;
        existenceReceived |= (byte) (1 << child);
    }
    boolean hasDescendants(int child) {
        if ((required & (1 << child)) == 0)
            throw new IllegalStateException("TerraVista Render: Tried getting child child existence set of child not in mask");
        return (existenceReceived & (1 << child)) != 0;
    }
    byte descendants(int child) {
        if (!hasDescendants(child))
            throw new IllegalStateException("TerraVista Render: Tried getting child child existence when child child existence for child was not set");
        return descendants[child];
    }
    int mesh(int child, int result) {
        if ((required & (1 << child)) == 0)
            throw new IllegalStateException("TerraVista Render: Tried setting child mesh when child isnt in mask");
        boolean first = (received & (1 << child)) == 0;
        received |= (byte) (1 << child);
        int previous = meshes[child];
        meshes[child] = result;
        return first ? -1 : previous;
    }
    int remove(int child) {
        byte bit = (byte) (1 << child);
        if ((required & bit) == 0)
            throw new IllegalStateException("TerraVista Render: Tried removing and unmasking child that was never masked");
        byte previous = received;
        received &= (byte) ~bit;
        required &= (byte) ~bit;
        existenceReceived &= (byte) ~bit;
        int mesh = meshes[child];
        meshes[child] = -1;
        return (previous & bit) == 0 ? -1 : mesh;
    }
    void require(int child) {
        byte bit = (byte) (1 << child);
        if ((required & bit) != 0) throw new IllegalStateException("TerraVista Render: Child already required!");
        required |= bit;
    }
    boolean satisfied() { return (received & required) == required; }
    long position() { return position; }
    byte mask() { return required; }
}

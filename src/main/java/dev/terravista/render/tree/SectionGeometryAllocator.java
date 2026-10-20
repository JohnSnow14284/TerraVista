package dev.terravista.render.tree;

import dev.terravista.mesh.SectionMesh;
import java.util.function.Consumer;

/** 定义 CPU 树通过分配队列执行的几何操作。 */
public interface SectionGeometryAllocator {
    int upload(SectionMesh mesh);
    int replace(int previous, SectionMesh mesh);
    void remove(int id);
    void downloadAndRemove(int id, Consumer<SectionMesh> callback);
}

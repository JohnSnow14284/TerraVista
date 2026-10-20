package dev.terravista.render.tree;

import dev.terravista.render.DeviceBuffer;

/** 在渲染线程将调用方 CPU 范围复制或散布到设备几何与节点。 */
public interface NodeUploadTarget {
    void copyRanges(long headers, int copies, long data, int bytes, MeshBufferStorage geometry);
    void scatter(long chunks, int count, DeviceBuffer nodes, MeshBufferStorage geometry);
    void close();
}

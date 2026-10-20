package dev.terravista.render.tree;

import dev.terravista.render.DeviceBuffer;

/** 定义树发布所需的设备几何与三十二字节区块元数据。 */
public interface MeshBufferStorage extends SectionGeometryStorage {
    int METADATA_BYTES = 32;
    DeviceBuffer geometryBuffer();
    DeviceBuffer metadataBuffer();
    void ensureElement(int maxElementAccess);
    void size(int count);
    int maxSections();
    long geometryBytes();
}

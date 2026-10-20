package dev.terravista.render.gpu;

import dev.terravista.render.tree.MeshBufferStorage;

/** 在渲染线程生命周期内持有完整网格与区块元数据。 */
public final class GpuGeometryTable implements MeshBufferStorage {
    private final GpuBuffer metadata;
    private final GpuBuffer geometry;
    private final int sectionLimit;
    private int sections;

    public GpuGeometryTable(FrameCommands commands, int sectionLimit, long geometryBytes) {
        this.sectionLimit = sectionLimit;
        if (geometryBytes % 8 != 0) throw new IllegalStateException();
        metadata = new GpuBuffer(commands, (long) sectionLimit * METADATA_BYTES);
        GpuBuffer allocated = null;
        try {
            allocated = GeometryAllocation.allocate(commands, geometryBytes);
            allocated.zero();
            metadata.zero();
        } catch (RuntimeException | Error failure) {
            // 通过帧回收等待已记录的填充操作完成后释放资源。
            if (allocated != null) allocated.free();
            metadata.free();
            throw failure;
        }
        geometry = allocated;
    }
    @Override public GpuBuffer geometryBuffer() { return geometry; }
    @Override public GpuBuffer metadataBuffer() { return metadata; }
    @Override public void ensureElement(int maxElementAccess) {
        // 完整驻留存储直接接收提交范围。
        return;
    }
    @Override public int size() { return sections; }
    @Override public void size(int count) { sections = count; }
    @Override public int maxSections() { return sectionLimit; }
    @Override public long geometryBytes() { return geometry.size(); }
    @Override public long maxCapacity() { return geometry.size(); }
    @Override public void close() {
        metadata.free();
        geometry.free();
    }
}

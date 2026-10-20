package dev.terravista.mesh;

import dev.terravista.memory.NativeBuffer;
import java.util.Arrays;

/** 管理区块几何、分区偏移与可选占用数据。 */
public final class SectionMesh {
    public final long position;
    public final byte children;
    public final int bounds;
    public final NativeBuffer geometry;
    public final int[] offsets;
    public final NativeBuffer occupancy;

    public SectionMesh(long position, byte children, int bounds, NativeBuffer geometry,
                       int[] offsets, NativeBuffer occupancy) {
        this.position = position;
        this.children = children;
        this.bounds = bounds;
        this.geometry = geometry;
        this.offsets = offsets;
        this.occupancy = occupancy;
    }
    public static SectionMesh empty(long position) { return empty(position, (byte) 0); }
    public static SectionMesh empty(long position, byte children) {
        return new SectionMesh(position, children, -1, null, null, null);
    }
    public SectionMesh copy() {
        return new SectionMesh(position, children, bounds, geometry != null ? geometry.duplicate() : null,
                offsets != null ? Arrays.copyOf(offsets, offsets.length) : null,
                occupancy != null ? occupancy.duplicate() : null);
    }
    public void free() {
        if (geometry != null) geometry.free();
        if (occupancy != null) occupancy.free();
    }
    public boolean isEmpty() { return geometry == null; }
}

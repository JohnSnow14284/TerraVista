package dev.terravista.mesh;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.NativeMemory;
import dev.terravista.model.ModelCatalog;
import dev.terravista.model.ModelMetadata;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.SectionContents;
import dev.terravista.world.ResidentSection;
import org.lwjgl.system.MemoryUtil;
import java.util.Arrays;

/** 管理八个四边形分区与边界并接收矩形流。 */
final class PackedQuadWriter {
    private final NativeBuffer scratch = new NativeBuffer(8 * (8 * (1 << 16)));
    private final long address = scratch.address;
    private final int[] counts = new int[8];
    private final ModelCatalog models;
    private final FluidSurface fluids;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int count;

    PackedQuadWriter(ModelCatalog models, FluidSurface fluids) { this.models = models; this.fluids = fluids; }
    final class Plane extends RectangleMesher {
        int position;
        boolean offset = true;
        int axis;
        @Override protected void emit(int x, int z, int length, int width, long data) {
            int model = (int) ((data >>> 26) & 0xFFFF);
            boolean fluid = ModelMetadata.fluid(models.metadata(model));
            int surfaceHeight = (int) (data >>> 3) & 511;
            if ((fluid || surfaceHeight != 0) && (length != 1 || width != 1)) {
                int firstX = x - (length - 1), firstZ = z - (width - 1);
                for (int cellZ = firstZ; cellZ <= z; cellZ++) for (int cellX = firstX; cellX <= x; cellX++)
                    emit(cellX, cellZ, 1, 1, data);
                return;
            }
            x -= length - 1;
            z -= width - 1;
            if (axis == 2) {
                int swap = x; x = z; z = swap;
                swap = length; length = width; width = swap;
            }
            int auxiliary = (int) (data & ((1 << 26) - 1));
            data &= ~((1L << 26) - 1);
            int side = auxiliary & 1;
            int type = (auxiliary >> 1) & 3;
            int plane = position;
            plane += 1 - (offset ? side : 1);
            int face = (axis << 1) | side;
            int encoded = face;
            if (fluid) {
                int cellX, cellY, cellZ;
                if (axis == 0) { cellX = x; cellY = plane; cellZ = z; }
                else if (axis == 1) { cellX = x; cellY = z; cellZ = plane; }
                else { cellX = plane; cellY = x; cellZ = z; }
                int heights = fluids.packedHeights(cellX, cellY, cellZ);
                if (face == 1) data = shadeFluid(data, model, fluids.iceShade(cellX, cellY, cellZ));
                encoded |= (heights & 0xFF) << 3;
                data = (data & ~(0xFL << 42)) | (Integer.toUnsignedLong((heights >>> 8) & 0xF) << 42);
            } else if (surfaceHeight != 0) {
                encoded |= (surfaceHeight & 255) << 3;
                data |= ((long) (surfaceHeight >>> 8) << 42) | (1L << 45);
                int cellX = axis == 2 ? plane : x;
                int cellY = axis == 0 ? plane : axis == 1 ? z : x;
                int cellZ = axis == 1 ? plane : z;
                if (ModelMetadata.translucent(models.metadata(model)) && fluids.iceSurface(cellX, cellY, cellZ)) {
                    // 未采集邻域不作为冰盖的实际边缘。
                    if (axis != 0 && fluids.unrecordedSide(cellX, cellY, cellZ, axis, side)) return;
                    data |= 1L << 43;
                }
            } else encoded |= ((width - 1) << 7) | ((length - 1) << 3);
            encoded |= x << (axis == 2 ? 16 : 21);
            encoded |= z << (axis == 1 ? 16 : 11);
            int shift = axis == 0 ? 16 : (axis == 1 ? 11 : 21);
            encoded |= plane << shift;
            long quad = data | Integer.toUnsignedLong(encoded);
            count++;
            int partition = type + (type == 2 ? face : 0);
            long destination = counts[partition]++ * 8L + partition * 8L * (1 << 16);
            MemoryUtil.memPutLong(address + destination, quad);
            extend(axis, plane, x, z, length, width);
        }
    }
    private long shadeFluid(long data, int model, float shade) {
        if (shade >= 1 || !ModelMetadata.biomeColoured(models.metadata(model))) return data;
        int biome = (int) (data >>> 46) & 511;
        int colour;
        if (data < 0) colour = models.blendedTints.colour(biome);
        else {
            int[] row = models.blendedTints.row(model);
            if (row == null || biome >= row.length) throw new dev.terravista.model.PendingModel(model, false);
            colour = row[biome];
        }
        int shaded = 0;
        for (int shift = 0; shift <= 16; shift += 8)
            shaded |= Math.round(((colour >>> shift) & 255) * shade) << shift;
        int tint = models.blendedTints.index(shaded);
        return (data & ~(511L << 46)) | ((long) tint << 46) | Long.MIN_VALUE;
    }
    Plane plane() { return new Plane(); }
    private void extend(int axis, int plane, int x, int z, int length, int width) {
        if (axis == 0) {
            minY = Math.min(minY, plane); maxY = Math.max(maxY, plane);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + length);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z + width);
        } else if (axis == 1) {
            minZ = Math.min(minZ, plane); maxZ = Math.max(maxZ, plane);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + length);
            minY = Math.min(minY, z); maxY = Math.max(maxY, z + width);
        } else {
            minX = Math.min(minX, plane); maxX = Math.max(maxX, plane);
            minY = Math.min(minY, x); maxY = Math.max(maxY, x + length);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z + width);
        }
    }
    void reset() {
        resetCount();
        resetBounds();
        resetPartitions();
    }
    void resetCount() { count = 0; }
    void resetBounds() {
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
    }
    void resetPartitions() { Arrays.fill(counts, 0); }
    int count() { return count; }
    SectionMesh finish(long position, byte children, OccupancyWords occupancy) {
        return finish(position, children, null, occupancy);
    }
    SectionMesh finish(ResidentSection section, OccupancyWords occupancy) {
        var contents = section.contents();
        return finish(contents.key, (byte) 0, contents, occupancy);
    }
    private SectionMesh finish(long position, byte children, SectionContents source, OccupancyWords occupancy) {
        if (count == 0) return SectionMesh.empty(position, source == null ? children : source.childMask());
        if (count >= 1 << 16)
            ModLog.warn("Mesh: Large quad count for section " + SectionCoordinates.describe(position) + " is " + count);
        if (minX < 0 || minY < 0 || minZ < 0 || 32 < maxX || 32 < maxY || 32 < maxZ) throw new IllegalStateException();
        int[] offsets = new int[8];
        var geometry = new NativeBuffer(count * 8L);
        int start = 0;
        for (int partition = 0; partition < 8; partition++) {
            offsets[partition] = start;
            int size = counts[partition];
            NativeMemory.copy(address + partition * (8 * (1 << 16)), geometry.address + start * 8L, size * 8L);
            start += size;
        }
        int bounds = 0;
        bounds |= minX; bounds |= minY << 5; bounds |= minZ << 10;
        bounds |= Math.max(0, maxX - minX - 1) << 15;
        bounds |= Math.max(0, maxY - minY - 1) << 20;
        bounds |= Math.max(0, maxZ - minZ - 1) << 25;
        NativeBuffer occupied = null;
        if (occupancy != null && !occupancy.isEmpty()) {
            occupied = new NativeBuffer(occupancy.bytes());
            occupancy.write(occupied.address, false);
        }
        return new SectionMesh(position, source == null ? children : source.childMask(), bounds, geometry, offsets, occupied);
    }
    void free() { scratch.free(); }
}

package dev.terravista.render.tree;

import dev.terravista.memory.FreeSlotIndex;
import dev.terravista.memory.NativeArena;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.mesh.SectionMesh;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryUtil;

/** 管理 CPU 几何分配与待发布变更，在同步时转移上传所有权。 */
public final class MeshAllocationQueue implements SectionGeometryAllocator {
    public static final int METADATA_BYTES = 32;
    private static final long ELEMENT_BYTES = 8;
    private final FreeSlotIndex slots;
    private final NativeArena arena = new NativeArena();
    private final ObjectArrayList<SectionMeshMetadata> metadata = new ObjectArrayList<>(1 << 15);
    private final IntOpenHashSet changed = new IntOpenHashSet(1024);
    private final Int2ObjectOpenHashMap<NativeBuffer> uploads = new Int2ObjectOpenHashMap<>(1024);
    private final IntOpenHashSet removals = new IntOpenHashSet(1024);
    private long usedElements;

    public MeshAllocationQueue(int limit, long capacity) {
        slots = new FreeSlotIndex(limit);
        if (capacity % ELEMENT_BYTES != 0) throw new IllegalStateException();
        arena.limit(capacity / ELEMENT_BYTES);
    }
    @Override public int upload(SectionMesh mesh) { return replace(-1, mesh); }
    @Override public int replace(int previous, SectionMesh mesh) {
        if (mesh.isEmpty()) throw new IllegalArgumentException("TerraVista Render: sectionData is empty, cannot upload nothing");
        if (previous != -1) remove(previous);
        int id = slots.allocate();
        if (id == FreeSlotIndex.FULL)
            throw new IllegalStateException("TerraVista Render: Tried adding section when section count is already at capacity");
        if (id > metadata.size())
            throw new IllegalStateException("TerraVista Render: Size exceeds limits: " + id + ", " + metadata.size() + ", " + slots.size());
        if (id < metadata.size()) {
            if (metadata.get(id) != null) throw new IllegalStateException();
        }
        var record = reserve(mesh);
        if (id == metadata.size()) metadata.add(record);
        else if (metadata.set(id, record) != null) throw new IllegalStateException();
        changed.add(id);
        return id;
    }
    private SectionMeshMetadata reserve(SectionMesh mesh) {
        if (mesh.geometry.bytes % ELEMENT_BYTES != 0) throw new IllegalStateException();
        int elements = (int) (mesh.geometry.bytes / ELEMENT_BYTES);
        int aligned = (elements + 127) & ~127;
        int address = (int) arena.allocate(aligned);
        if (address == -1)
            throw new IllegalStateException("TerraVista Render: Geometry OOM. requested allocation size (in elements): " + elements
                    + ", Heap size at top remaining: " + (arena.limit() - arena.extent()) + ", used elements: " + usedElements);
        usedElements += aligned;
        if (uploads.put(address, mesh.geometry) != null) throw new IllegalStateException("TerraVista Render: Addr: " + address);
        removals.remove(address);
        return new SectionMeshMetadata(mesh.position, mesh.bounds, address, elements, mesh.offsets, mesh.children);
    }
    @Override public void remove(int id) {
        if (!slots.release(id)) throw new IllegalStateException("TerraVista Render: Id was not already allocated. id: " + id);
        var record = metadata.set(id, null);
        int address = record.address();
        usedElements -= arena.release(Integer.toUnsignedLong(address));
        var buffer = uploads.remove(address);
        if (buffer != null) buffer.free();
        removals.add(address);
        changed.add(id);
    }
    /** 拒绝 CPU 分配队列中的读回请求。 */
    @Override public void downloadAndRemove(int id, Consumer<SectionMesh> callback) {
        throw new IllegalStateException("TerraVista Render: Position enumeration is unavailable for the LMDB backend");
    }
    public Int2ObjectOpenHashMap<NativeBuffer> uploads() { return uploads; }
    public IntOpenHashSet removals() { return removals; }
    public IntOpenHashSet changedIds() { return changed; }
    public int size() { return slots.size(); }
    public long usedBytes() { return usedElements * ELEMENT_BYTES; }
    public void writeMetadata(int id, long address) {
        var record = metadata.get(id);
        if (record == null) MemoryUtil.memSet(address, 0, METADATA_BYTES);
        else record.write(address, address + 16);
    }
    public void writeMetadataSplit(int id, long first, long second) {
        if (METADATA_BYTES != 32) throw new IllegalStateException();
        var record = metadata.get(id);
        if (record == null) {
            MemoryUtil.memSet(first, 0, 16);
            MemoryUtil.memSet(second, 0, 16);
        } else record.write(first, second);
    }
}

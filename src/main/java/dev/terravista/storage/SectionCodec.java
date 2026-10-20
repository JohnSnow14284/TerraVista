package dev.terravista.storage;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.NativeCleaner;
import dev.terravista.world.SectionContents;
import dev.terravista.world.VoxelBits;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import org.lwjgl.system.MemoryUtil;

/** 读写版本化区段头、紧凑索引与首见顺序体素表。 */
public final class SectionCodec {
    public static final int STORAGE_VERSION = 1;
    private record Workspace(Long2ShortOpenHashMap lookup, NativeBuffer buffer) {
        Workspace() {
            this(new Long2ShortOpenHashMap(1024), scratch());
            lookup.defaultReturnValue((short) -1);
        }
        private static NativeBuffer scratch() {
            var allocation = new NativeBuffer(SectionContents.VOLUME * 2 + SectionContents.VOLUME * 8 + 1024);
            var view = allocation.borrow();
            NativeCleaner.INSTANCE.register(view, allocation::free);
            return view;
        }
    }
    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);
    private SectionCodec() {}
    public static int linearToMorton(int index) {
        int x = index & 0x1F, y = (index >> 10) & 0x1F, z = (index >> 5) & 0x1F;
        return Integer.expand(x, 0b1001001001001) | Integer.expand(y, 0b10010010010010) | Integer.expand(z, 0b100100100100100);
    }
    public static int mortonToLinear(int index) {
        int x = Integer.compress(index, 0b1001001001001), y = Integer.compress(index, 0b10010010010010), z = Integer.compress(index, 0b100100100100100);
        return x | (y << 10) | (z << 5);
    }
    public static NativeBuffer encode(SectionContents section) {
        var workspace = WORKSPACE.get();
        var blocks = section.blocks();
        var lookup = workspace.lookup;
        lookup.clear();
        var buffer = workspace.buffer.borrow();
        long pointer = buffer.address;
        MemoryUtil.memPutLong(pointer, section.key); pointer += 8;
        long metadataAddress = pointer; pointer += 8;
        long indices = pointer; pointer += SectionContents.VOLUME * 2;
        long originalTable = pointer;
        long previous = blocks[0];
        MemoryUtil.memPutLong(pointer, previous); pointer += 8;
        lookup.put(previous, (short) 0);
        short mapping = 0;
        for (long block : blocks) {
            if (previous != block) {
                previous = block;
                mapping = lookup.putIfAbsent(block, (short) lookup.size());
                if (mapping == -1) {
                    mapping = (short) (lookup.size() - 1);
                    MemoryUtil.memPutLong(pointer, block); pointer += 8;
                }
            }
            MemoryUtil.memPutShort(indices, mapping); indices += 2;
        }
        if (lookup.size() >= 1 << 16) throw new IllegalStateException();
        long metadata = Integer.toUnsignedLong(lookup.size());
        metadata |= Byte.toUnsignedLong(section.childMask()) << 16;
        metadata |= (long) STORAGE_VERSION << 24;
        MemoryUtil.memPutLong(metadataAddress, metadata);
        int bits = 32 - Integer.numberOfLeadingZeros(lookup.size() - 1);
        long output = buffer.address + 16;
        if (bits != 0) {
            long packed = 0;
            int used = 0;
            for (int i = 0; i < SectionContents.VOLUME; i++) {
                long value = Short.toUnsignedLong(MemoryUtil.memGetShort(buffer.address + 16 + i * 2L));
                packed |= value << used;
                used += bits;
                if (used >= 64) {
                    MemoryUtil.memPutLong(output, packed); output += 8;
                    used -= 64;
                    packed = value >>> (bits - used);
                }
            }
        }
        // 向前移动调色板，允许源区和目标区重叠。
        for (int i = 0; i < lookup.size(); i++) {
            MemoryUtil.memPutLong(output, MemoryUtil.memGetLong(originalTable + i * 8L));
            output += 8;
        }
        return buffer.resizeView(output - buffer.address);
    }
    public static boolean decode(SectionContents section, NativeBuffer data) {
        final long headerBytes = 16;
        if (data.bytes < headerBytes + 8 || data.bytes > SectionFormat.MAX_SERIALIZED_BYTES) return false;
        long pointer = data.address;
        long key = MemoryUtil.memGetLong(pointer); pointer += 8;
        if (section.key != key) {
            ModLog.error("Storage: Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }
        long metadata = MemoryUtil.memGetLong(pointer); pointer += 8;
        int entries = (int) (metadata & 0xFFFF);
        long version = metadata >>> 24;
        if (entries < 1 || entries > SectionContents.VOLUME || (version != 0 && version != STORAGE_VERSION)) return false;
        int bits = version == 0 ? 16 : 32 - Integer.numberOfLeadingZeros(entries - 1);
        long indexBytes = SectionContents.VOLUME * (long) bits / 8;
        if (data.bytes != headerBytes + indexBytes + entries * 8L) return false;
        // 校验全部索引后再修改目标区段。
        for (int i = 0; i < SectionContents.VOLUME; i++) {
            if (index(pointer, i, bits, version == 0) >= entries) return false;
        }
        long table = pointer + indexBytes;
        var blocks = section.blocks();
        for (int i = 0; i < SectionContents.VOLUME; i++) {
            blocks[i] = MemoryUtil.memGetLong(table + index(pointer, i, bits, version == 0) * 8L);
        }
        section.restoreChildMask((byte) ((metadata >>> 16) & 0xFF));
        if (section.level == 0) {
            int occupied = 0;
            for (long block : blocks) occupied += VoxelBits.nonAir(block);
            section.restoreBlockCount(occupied);
        }
        return true;
    }
    private static int index(long pointer, int cell, int bits, boolean legacy) {
        if (legacy) return Short.toUnsignedInt(MemoryUtil.memGetShort(pointer + cell * 2L));
        if (bits == 0) return 0;
        int offset = cell * bits, shift = offset & 63;
        long address = pointer + (offset >>> 6) * 8L;
        long value = MemoryUtil.memGetLong(address) >>> shift;
        if (shift + bits > 64) value |= MemoryUtil.memGetLong(address + 8) << (64 - shift);
        return (int) (value & ((1 << bits) - 1));
    }
}

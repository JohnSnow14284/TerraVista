package dev.terravista.render.tree;

import org.lwjgl.system.MemoryUtil;

/** 定义三十二字节着色器元数据并保留分区偏移数组引用。 */
record SectionMeshMetadata(long position, int bounds, int address, int elements, int[] offsets, byte children) {
    void write(long first, long second) {
        MemoryUtil.memPutInt(first, (int) (position >> 32)); first += 4;
        MemoryUtil.memPutInt(first, (int) position); first += 4;
        MemoryUtil.memPutInt(first, bounds); first += 4;
        MemoryUtil.memPutInt(first, address + offsets[0]); first += 4;
        MemoryUtil.memPutInt(second, (offsets[1] - offsets[0]) | ((offsets[2] - offsets[1]) << 16)); second += 4;
        MemoryUtil.memPutInt(second, (offsets[3] - offsets[2]) | ((offsets[4] - offsets[3]) << 16)); second += 4;
        MemoryUtil.memPutInt(second, (offsets[5] - offsets[4]) | ((offsets[6] - offsets[5]) << 16)); second += 4;
        MemoryUtil.memPutInt(second, (offsets[7] - offsets[6]) | ((elements - offsets[7]) << 16)); second += 4;
    }
}

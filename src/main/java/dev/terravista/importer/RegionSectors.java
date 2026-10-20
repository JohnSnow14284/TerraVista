package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

/** 按区域索引顺序校验扇区并复制区块载荷。 */
final class RegionSectors {
    @FunctionalInterface
    interface PayloadConsumer { void accept(byte format, NativeBuffer payload); }

    private RegionSectors() {}

    static void scan(NativeBuffer region, PayloadConsumer consumer) {
        if (region.bytes < 8192) {
            ModLog.warn("Import: Region file header is invalid");
            return;
        }
        for (int index = 0; index < 1024; index++) {
            int location = Integer.reverseBytes(MemoryUtil.memGetInt(region.address + index * 4));
            if (location == 0) continue;
            int start = location >>> 8;
            int count = location & 255;
            if (count == 0) continue;
            if (region.bytes < ((count - 1) + start) * 4096L) {
                ModLog.warn("Import: Cannot access chunk sector as it goes out of bounds. start bytes: " + (start * 4096)
                        + " sector count: " + count + " fileSize: " + region.bytes);
                continue;
            }
            copyPayload(region, start, count, consumer);
        }
    }

    private static void copyPayload(NativeBuffer region, int start, int count, PayloadConsumer consumer) {
        long base = region.address + start * 4096L;
        int capacity = count * 4096;
        int declared = Integer.reverseBytes(MemoryUtil.memGetInt(base));
        byte format = MemoryUtil.memGetByte(base + 4);
        if (declared == 0) {
            ModLog.error("Import: Chunk is allocated, but stream is missing");
            return;
        }
        int length = declared - 1;
        if (region.bytes < length + start * 4096L) {
            ModLog.warn("Import: Chunk stream is shorter than its header");
        } else if ((format & 128) != 0) {
            if (length != 0) ModLog.error("Import: Chunk has both internal and external streams");
            ModLog.error("Import: Chunk has external stream which is not supported");
        } else if (length > capacity - 5) {
            ModLog.error("Import: Chunk stream is truncated: expected " + length + " but read " + (capacity - 5));
        } else if (length < 0) {
            ModLog.error("Import: Declared size of chunk is negative");
        } else {
            consumer.accept(format, new NativeBuffer(length).copyFrom(base + 5));
        }
    }
}

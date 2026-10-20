package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.NativeMemory;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.lwjgl.system.MemoryUtil;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 按区域压缩标记读取借用的区块内存。 */
final class RegionChunkStreams {
    private RegionChunkStreams() {}

    static DataInputStream decode(byte format, NativeBuffer payload) throws IOException {
        var version = RegionFileVersion.fromId(format);
        if (version == null) {
            ModLog.error("Import: Chunk has invalid chunk stream version");
            return null;
        }
        return new DataInputStream(version.wrap(raw(payload)));
    }

    static InputStream raw(NativeBuffer payload) {
        return new PayloadStream(payload);
    }

    /** 顺序读取区块字节，不接管内存释放。 */
    private static final class PayloadStream extends InputStream {
        private final NativeBuffer payload;
        private long cursor;

        private PayloadStream(NativeBuffer payload) { this.payload = payload; }

        @Override public int read() {
            return MemoryUtil.memGetByte(payload.address + cursor++) & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) {
            int copied = Math.min(length, available());
            if (copied == 0) return -1;
            NativeMemory.copy(payload.address + cursor, copied, target, offset);
            cursor += copied;
            return copied;
        }

        @Override public int available() { return (int) (payload.bytes - cursor); }
    }
}

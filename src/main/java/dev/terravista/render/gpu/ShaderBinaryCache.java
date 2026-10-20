package dev.terravista.render.gpu;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

/** 在编译监视器保护下缓存 Java 持有的 SPIR-V。 */
final class ShaderBinaryCache {
    private static final int MAX_BYTES = 16 << 20;
    private static final int MAX_ENTRIES = 128;
    private final LinkedHashMap<Key, byte[]> entries = new LinkedHashMap<>(32, 0.75f, true);
    private long bytes;

    record Key(String source, ShaderStage stage, String name) {
        long size(int binaryBytes) {
            return 2L * (source.length() + name.length()) + binaryBytes;
        }
    }

    byte[] find(Key key) { return entries.get(key); }

    void remember(Key key, ByteBuffer binary) {
        long size = key.size(binary.remaining());
        if (size <= MAX_BYTES) {
            while (!entries.isEmpty() && (bytes + size > MAX_BYTES || entries.size() >= MAX_ENTRIES)) {
                var oldest = entries.pollFirstEntry();
                bytes -= oldest.getKey().size(oldest.getValue().length);
            }
            byte[] copy = new byte[binary.remaining()];
            binary.duplicate().get(copy);
            entries.put(key, copy);
            bytes += size;
        }
    }
}

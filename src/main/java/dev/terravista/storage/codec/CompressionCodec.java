package dev.terravista.storage.codec;

import dev.terravista.memory.NativeBuffer;

/** 在线程暂存的下一次操作前借用当前结果。 */
public interface CompressionCodec {
    NativeBuffer encode(NativeBuffer input);
    NativeBuffer decode(NativeBuffer input);
}

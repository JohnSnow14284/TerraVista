package dev.terravista.storage.codec;

import dev.terravista.memory.NativeCleaner;
import static org.lwjgl.util.zstd.Zstd.*;

/** 在线程局部上下文中共享原生分配所有权。 */
final class ZstdThreadContexts {
    private record Handle(long address) {}
    private final ThreadLocal<Handle> compression = ThreadLocal.withInitial(ZstdThreadContexts::compressionHandle);
    private final ThreadLocal<Handle> decompression = ThreadLocal.withInitial(ZstdThreadContexts::decompressionHandle);

    private static Handle compressionHandle() {
        long address = ZSTD_createCCtx();
        var handle = new Handle(address);
        NativeCleaner.INSTANCE.register(handle, () -> ZSTD_freeCCtx(address));
        return handle;
    }
    private static Handle decompressionHandle() {
        long address = ZSTD_createDCtx();
        // 按顺序设置忽略校验和的实验参数。
        nZSTD_DCtx_setParameter(address, ZSTD_d_experimentalParam3, 1);
        var handle = new Handle(address);
        NativeCleaner.INSTANCE.register(handle, () -> ZSTD_freeDCtx(address));
        return handle;
    }
    long compression() { return compression.get().address; }
    long decompression() { return decompression.get().address; }
}

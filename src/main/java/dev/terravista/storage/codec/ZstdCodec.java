package dev.terravista.storage.codec;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.ResizableNativeScratch;
import dev.terravista.storage.SectionFormat;
import dev.terravista.storage.CorruptSectionException;
import dev.terravista.config.StorageBuildContext;
import static org.lwjgl.util.zstd.Zstd.*;

/** 通过借用暂存内存执行 ZSTD 帧压缩与解压。 */
public final class ZstdCodec implements CompressionCodec {
    private static final ZstdThreadContexts CONTEXTS = new ZstdThreadContexts();
    private static final ResizableNativeScratch SCRATCH = new ResizableNativeScratch(SectionFormat.MAX_SERIALIZED_BYTES + 1024);
    private final int level;

    public ZstdCodec(int level) { this.level = level; }

    @Override public NativeBuffer encode(NativeBuffer input) {
        var output = SCRATCH.get(ZSTD_COMPRESSBOUND(input.bytes)).borrow();
        long bytes = nZSTD_compressCCtx(CONTEXTS.compression(), output.address, output.bytes, input.address, input.bytes, level);
        if (ZSTD_isError(bytes)) throw new IllegalStateException("TerraVista Storage: ZSTD compression failed: " + ZSTD_getErrorName(bytes));
        return output.resizeView(bytes);
    }
    @Override public NativeBuffer decode(NativeBuffer input) {
        if (input.bytes <= 0 || input.bytes > ZSTD_COMPRESSBOUND(SectionFormat.MAX_SERIALIZED_BYTES))
            throw new CorruptSectionException("TerraVista Storage: Invalid ZSTD block length");
        var output = SCRATCH.get().borrow();
        long bytes = nZSTD_decompressDCtx(CONTEXTS.decompression(), output.address, SectionFormat.MAX_SERIALIZED_BYTES, input.address, input.bytes);
        if (ZSTD_isError(bytes)) throw new CorruptSectionException("TerraVista Storage: ZSTD block could not be decoded: " + ZSTD_getErrorName(bytes));
        if (bytes <= 0) throw new CorruptSectionException("TerraVista Storage: Empty ZSTD output");
        return output.resizeView(bytes);
    }
    public static final class Config extends CodecConfig {
        public int compressionLevel;
        @Override public CompressionCodec build(StorageBuildContext context) { return new ZstdCodec(compressionLevel); }
        public static String getConfigTypeName() { return "ZSTD"; }
    }
}

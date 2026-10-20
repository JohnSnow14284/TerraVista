package dev.terravista.storage.codec;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.ResizableNativeScratch;
import dev.terravista.storage.SectionFormat;
import dev.terravista.storage.CorruptSectionException;
import dev.terravista.config.StorageBuildContext;
import net.jpountz.lz4.LZ4Factory;
import org.lwjgl.system.MemoryUtil;

/** 使用带四字节原始长度前缀的 LZ4 块格式。 */
public final class Lz4Codec implements CompressionCodec {
    private static final ResizableNativeScratch SCRATCH = new ResizableNativeScratch(SectionFormat.MAX_SERIALIZED_BYTES + 1024);
    private final net.jpountz.lz4.LZ4Compressor encoder;
    private final net.jpountz.lz4.LZ4SafeDecompressor decoder;

    public Lz4Codec() {
        decoder = LZ4Factory.nativeInstance().safeDecompressor();
        encoder = LZ4Factory.nativeInstance().fastCompressor();
    }

    @Override public NativeBuffer encode(NativeBuffer input) {
        var output = SCRATCH.get(encoder.maxCompressedLength((int) input.bytes) + 4).borrow();
        MemoryUtil.memPutInt(output.address, (int) input.bytes);
        int bytes = encoder.compress(input.byteBuffer(), 0, (int) input.bytes, output.byteBuffer(), 4, (int) output.bytes - 4);
        return output.resizeView(bytes + 4);
    }

    @Override public NativeBuffer decode(NativeBuffer input) {
        if (input.bytes <= 4 || input.bytes > encoder.maxCompressedLength(SectionFormat.MAX_SERIALIZED_BYTES) + 4L)
            throw new CorruptSectionException("TerraVista Storage: Invalid LZ4 block length");
        int length = MemoryUtil.memGetInt(input.address);
        if (length <= 0 || length > SectionFormat.MAX_SERIALIZED_BYTES)
            throw new CorruptSectionException("TerraVista Storage: Invalid LZ4 output length");
        var output = SCRATCH.get(length).borrow();
        try {
            int restored = decoder.decompress(input.byteBuffer(), 4, (int) input.bytes - 4,
                    output.byteBuffer(), 0, length);
            if (restored != length) throw new CorruptSectionException("TerraVista Storage: Incomplete LZ4 output");
            return output.resizeView(length);
        } catch (net.jpountz.lz4.LZ4Exception exception) {
            throw new CorruptSectionException("TerraVista Storage: LZ4 block could not be decoded", exception);
        }
    }
    public static final class Config extends CodecConfig {
        @Override public CompressionCodec build(StorageBuildContext context) { return new Lz4Codec(); }
        public static String getConfigTypeName() { return "LZ4"; }
    }
}

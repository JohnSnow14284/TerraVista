package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.codec.CodecConfig;
import dev.terravista.storage.codec.CompressionCodec;

/** 压缩区块字节，调色板与生命周期交由底层存储管理。 */
public final class CompressedByteStore extends ForwardingByteStore {
    private final CompressionCodec codec;
    public CompressedByteStore(CompressionCodec codec, ByteStore delegate) { super(delegate); this.codec = codec; }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) {
        var data = delegate.read(key, scratch);
        if (data == null) return null;
        return codec.decode(data);
    }
    @Override public void put(long key, NativeBuffer data) {
        var encoded = codec.encode(data);
        delegate.put(key, encoded);
    }
    public static final class Config extends DelegateStoreConfig {
        public CodecConfig compressor;
        @Override public ByteStore build(StorageBuildContext context) { return new CompressedByteStore(compressor.build(context), delegate.build(context)); }
        public static String getConfigTypeName() { return "CompressionAdaptor"; }
    }
}

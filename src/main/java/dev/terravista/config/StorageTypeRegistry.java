package dev.terravista.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.terravista.storage.*;
import dev.terravista.storage.backend.*;
import dev.terravista.storage.codec.*;
import java.util.List;

/** 按固定顺序注册十三种存储配置类型。 */
public final class StorageTypeRegistry {
    private record Registration(Class<?> family, String name, Class<?> type) {}
    private static final List<Registration> REGISTRATIONS = List.of(
            new Registration(CodecConfig.class, "LZ4", Lz4Codec.Config.class),
            new Registration(CodecConfig.class, "ZSTD", ZstdCodec.Config.class),
            new Registration(SectionStoreConfig.class, "Serializer", SectionPersistence.Config.class),
            new Registration(ByteStoreConfig.class, "Memory", MemoryByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "LMDB", LmdbByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "BasicPathConfig", PathStoreConfig.class),
            new Registration(ByteStoreConfig.class, "CompressionAdaptor", CompressedByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "ConditionalConfig", ConditionalStoreConfig.class),
            new Registration(ByteStoreConfig.class, "FragmentationAdaptor", FragmentedByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "AutoFragmentationAdaptor", AutomaticFragmentConfig.class),
            new Registration(ByteStoreConfig.class, "ReadonlyCachingLayer", ReadCacheByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "Redis", RedisByteStore.Config.class),
            new Registration(ByteStoreConfig.class, "RocksDB", RocksByteStore.Config.class));
    private StorageTypeRegistry() {}
    @SuppressWarnings("unchecked")
    public static Gson createJson() {
        var stores = new TaggedConfigAdapter<>(ByteStoreConfig.class);
        var codecs = new TaggedConfigAdapter<>(CodecConfig.class);
        var sections = new TaggedConfigAdapter<>(SectionStoreConfig.class);
        for (var entry : REGISTRATIONS) {
            if (entry.family == ByteStoreConfig.class) stores.register(entry.name, (Class<? extends ByteStoreConfig>) entry.type);
            else if (entry.family == CodecConfig.class) codecs.register(entry.name, (Class<? extends CodecConfig>) entry.type);
            else sections.register(entry.name, (Class<? extends SectionStoreConfig>) entry.type);
        }
        return new GsonBuilder().setPrettyPrinting().registerTypeAdapterFactory(stores)
                .registerTypeAdapterFactory(codecs).registerTypeAdapterFactory(sections).create();
    }
    public static SectionPersistence.Config defaultSerializer() {
        var backend = new RocksByteStore.Config();
        var codec = new ZstdCodec.Config();
        codec.compressionLevel = 1;
        var compression = new CompressedByteStore.Config();
        compression.delegate = backend; compression.compressor = codec;
        var serializer = new SectionPersistence.Config();
        serializer.storage = compression;
        return serializer;
    }
}

package dev.terravista.storage.codec;

import dev.terravista.config.StorageBuildContext;

/** 定义存储 JSON 与压缩存储使用的压缩器配置。 */
public abstract class CodecConfig {
    public abstract CompressionCodec build(StorageBuildContext context);
}

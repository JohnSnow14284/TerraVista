package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;

/** 定义 JSON 中区块存储的构造配置类型。 */
public abstract class SectionStoreConfig {
    public abstract SectionStore build(StorageBuildContext context);
}

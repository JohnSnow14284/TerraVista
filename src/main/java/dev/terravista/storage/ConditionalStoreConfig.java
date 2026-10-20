package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import org.apache.commons.lang3.NotImplementedException;

/** 拒绝构建不受支持的条件存储配置。 */
public final class ConditionalStoreConfig extends ByteStoreConfig {
    @Override public ByteStore build(StorageBuildContext context) { throw new NotImplementedException(); }
    public static String getConfigTypeName() { return "ConditionalConfig"; }
}

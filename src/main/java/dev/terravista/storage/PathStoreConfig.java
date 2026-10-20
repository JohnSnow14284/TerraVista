package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;

/** 子存储构造成功后弹出路径作用域。 */
public final class PathStoreConfig extends DelegateStoreConfig {
    public String path = "";
    @Override public ByteStore build(StorageBuildContext context) {
        context.push(path);
        var store = delegate.build(context);
        context.pop();
        return store;
    }
    public static String getConfigTypeName() { return "BasicPathConfig"; }
}

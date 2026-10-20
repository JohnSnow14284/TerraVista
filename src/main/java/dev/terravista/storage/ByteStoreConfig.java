package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import java.util.ArrayList;
import java.util.List;

/** 定义存储构造节点并通过 TYPE 注册表选择实现。 */
public abstract class ByteStoreConfig {
    public abstract ByteStore build(StorageBuildContext context);
    public List<ByteStoreConfig> children() { return List.of(); }
    public final List<ByteStoreConfig> descendants() {
        var configs = new ArrayList<ByteStoreConfig>();
        configs.add(this);
        for (var child : children()) configs.addAll(child.descendants());
        return configs;
    }
}

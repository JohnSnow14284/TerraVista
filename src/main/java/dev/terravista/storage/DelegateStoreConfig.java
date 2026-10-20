package dev.terravista.storage;

import java.util.List;

/** 通过序列化委托字段遍历单个子构造节点。 */
public abstract class DelegateStoreConfig extends ByteStoreConfig {
    public ByteStoreConfig delegate;
    @Override public List<ByteStoreConfig> children() { return List.of(delegate); }
}

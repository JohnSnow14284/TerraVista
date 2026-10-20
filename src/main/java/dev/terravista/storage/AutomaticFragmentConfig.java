package dev.terravista.storage;

import dev.terravista.config.StorageBuildContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 为重复构造的子存储绑定带索引的路径作用域。 */
public final class AutomaticFragmentConfig extends ByteStoreConfig {
    public ByteStoreConfig delegate;
    public String basePath;
    public int count;
    @Override public List<ByteStoreConfig> children() { return new ArrayList<>(Collections.singleton(delegate)); }
    @Override public ByteStore build(StorageBuildContext context) {
        var stores = new ByteStore[count];
        for (int i = 0; i < count; i++) {
            context.push(basePath + "_" + i);
            stores[i] = delegate.build(context);
            context.pop();
        }
        return new FragmentedByteStore(stores);
    }
    public static String getConfigTypeName() { return "AutoFragmentationAdaptor"; }
}

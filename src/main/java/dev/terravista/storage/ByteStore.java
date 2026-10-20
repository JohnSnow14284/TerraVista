package dev.terravista.storage;

import dev.terravista.memory.NativeBuffer;
import java.util.ArrayList;
import java.util.List;

/** 定义区块字节与调色板持久化，存储持有其后端层级。 */
public abstract class ByteStore implements PaletteStorage, StoredSectionVisitor {
    /** 可将暂存包装移交给扩容结果，由结果继续持有底层分配。 */
    public abstract NativeBuffer read(long key, NativeBuffer scratch);
    public abstract void put(long key, NativeBuffer data);
    public abstract void delete(long key);
    public List<ByteStore> children() { return List.of(); }
    public final List<ByteStore> descendants() {
        var stores = new ArrayList<ByteStore>();
        stores.add(this);
        for (var child : children()) stores.addAll(child.descendants());
        return stores;
    }
    /** 内存叶节点直接结束刷新，组合存储按顺序刷新子项。 */
    @Override public void flush() { for (var child : children()) child.flush(); }
}

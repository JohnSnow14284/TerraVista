package dev.terravista.storage;

import dev.terravista.memory.NativeBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;

/** 转发单子存储的区块字节变换操作。 */
public class ForwardingByteStore extends ByteStore {
    protected final ByteStore delegate;
    public ForwardingByteStore(ByteStore delegate) { this.delegate = delegate; }
    @Override public NativeBuffer read(long key, NativeBuffer scratch) { return delegate.read(key, scratch); }
    @Override public void put(long key, NativeBuffer data) { delegate.put(key, data); }
    @Override public void delete(long key) { delegate.delete(key); }
    @Override public void iteratePositions(int level, LongConsumer consumer) { delegate.iteratePositions(level, consumer); }
    @Override public void putIdMapping(int id, ByteBuffer data) { delegate.putIdMapping(id, data); }
    @Override public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return delegate.getIdMappingsData(); }
    @Override public void flush() { delegate.flush(); }
    @Override public void close() { delegate.close(); }
    @Override public List<ByteStore> children() { return List.of(delegate); }
}

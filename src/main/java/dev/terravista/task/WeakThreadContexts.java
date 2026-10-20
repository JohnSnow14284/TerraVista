package dev.terravista.task;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 通过十六个分片管理弱线程标识与上下文清理。 */
public final class WeakThreadContexts<K extends LongSupplier,V> {
    private static final class Bucket<V> {
        final ReentrantLock lock = new ReentrantLock();
        final Long2ObjectOpenHashMap<V> values = new Long2ObjectOpenHashMap<>();
    }
    private final Consumer<V> release;
    private final ReferenceQueue<K> deadKeys = new ReferenceQueue<>();
    private final ReentrantLock identitiesLock = new ReentrantLock();
    private final Object2LongOpenHashMap<WeakReference<K>> identities = new Object2LongOpenHashMap<>();
    private final Bucket<V>[] buckets;
    private final AtomicInteger count = new AtomicInteger();
    @SuppressWarnings("unchecked")
    public WeakThreadContexts(Consumer<V> release) {
        this.release = release;
        identities.defaultReturnValue(-1);
        buckets = new Bucket[16];
        for (int i=0;i<buckets.length;i++) buckets[i] = new Bucket<>();
    }
    private Bucket<V> bucket(long id) { return buckets[HashCommon.mix((int)id) & (buckets.length-1)]; }
    public V getOrCreate(K key, Supplier<V> factory) {
        cleanup();
        long id = key.getAsLong();
        var bucket = bucket(id);
        bucket.lock.lock();
        try {
            if (bucket.values.containsKey(id)) return bucket.values.get(id);
            V value = factory.get();
            identitiesLock.lock();
            try {
                identities.put(new WeakReference<>(key, deadKeys), id);
                bucket.values.put(id, value);
                count.incrementAndGet();
            } finally { identitiesLock.unlock(); }
            return value;
        } finally {
            bucket.lock.unlock();
        }
    }
    @SuppressWarnings("unchecked")
    public void cleanup() {
        WeakReference<K> reference = (WeakReference<K>) deadKeys.poll();
        if (reference == null) return;
        var pending = new LongArrayFIFOQueue();
        identitiesLock.lock();
        try {
            do {
                long id = identities.removeLong(reference);
                if (id >= 0) pending.enqueue(id);
            } while ((reference = (WeakReference<K>) deadKeys.poll()) != null);
        } finally { identitiesLock.unlock(); }
        if (pending.isEmpty()) return;
        RuntimeException failure = null;
        while (!pending.isEmpty()) {
            long id = pending.dequeueLong();
            var bucket = bucket(id);
            bucket.lock.lock();
            V value;
            try {
                value = bucket.values.remove(id);
                if (value != null && count.decrementAndGet() < 0) throw new IllegalStateException();
            } finally { bucket.lock.unlock(); }
            if (value != null) {
                try { release.accept(value); }
                catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) throw failure;
    }
    public List<V> clear() {
        var values = new ArrayList<V>(size());
        for (var bucket : buckets) bucket.lock.lock();
        try {
            identitiesLock.lock();
            try {
                identities.clear();
                for (var bucket : buckets) { values.addAll(bucket.values.values()); bucket.values.clear(); }
                count.set(0);
            } finally { identitiesLock.unlock(); }
        } finally {
            for (int index = buckets.length - 1; index >= 0; index--) buckets[index].lock.unlock();
        }
        return values;
    }
    public int size() { return count.get(); }
}

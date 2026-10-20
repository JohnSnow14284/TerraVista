package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiPredicate;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/** 分片管理活动区块与有序休眠缓存，退役前先保存。 */
public final class SectionResidency {
    private final AtomicInteger liveCount = new AtomicInteger();
    private final Long2ObjectOpenHashMap<SectionLoadTicket>[] live;
    private final StampedLock[] locks;
    private final ToIntFunction<ResidentSection> loader;
    private final int dormantLimit;
    private final StampedLock dormantLock = new StampedLock();
    private final Long2ObjectLinkedOpenHashMap<ResidentSection> dormant;
    private final Runnable activity;
    private final BiPredicate<ResidentSection, Boolean> save;
    private final ObjIntConsumer<ResidentSection> retirement = this::tryUnload;

    public SectionResidency(int sliceBits, ToIntFunction<ResidentSection> loader, int cacheSize) {
        this(sliceBits, loader, cacheSize, null, null);
    }
    /** 向保存回调传递非阻塞标记及已持有的退役区块引用。 */
    @SuppressWarnings("unchecked")
    public SectionResidency(int sliceBits, ToIntFunction<ResidentSection> loader, int cacheSize,
                            Runnable activity, BiPredicate<ResidentSection, Boolean> save) {
        this.activity = activity; this.save = save; this.loader = loader;
        live = new Long2ObjectOpenHashMap[1 << sliceBits];
        dormant = new Long2ObjectLinkedOpenHashMap<>(cacheSize);
        locks = new StampedLock[1 << sliceBits]; dormantLimit = cacheSize;
        for (int index = 0; index < live.length; index++) {
            live[index] = new Long2ObjectOpenHashMap<>(1024);
            locks[index] = new StampedLock();
        }
    }
    public boolean owns(ResidentSection section) { return section.retirement == retirement; }
    public ResidentSection acquire(int level, int x, int y, int z, boolean nullOnEmpty) {
        return acquire(SectionCoordinates.key(level, x, y, z), nullOnEmpty);
    }
    public ResidentSection acquire(long key, boolean nullOnEmpty) {
        if (activity != null) activity.run();
        int index = slice(key);
        var cache = live[index]; var lock = locks[index];
        SectionLoadTicket ticket;
        boolean loading = false;
        ResidentSection section = null;
        {
            long stamp = lock.readLock();
            boolean writing = false;
            try {
                ticket = cache.get(key);
                if (ticket != null) {
                    section = ticket.section;
                    if (section != null) {
                        section.acquire(); return section;
                    }
                } else {
                    ticket = new SectionLoadTicket();
                    long write = lock.tryConvertToWriteLock(stamp);
                    if (write == 0) { lock.unlockRead(stamp); stamp = lock.writeLock(); }
                    else stamp = write;
                    writing = true;
                    var existing = cache.putIfAbsent(key, ticket);
                    if (existing == null) loading = true;
                    else ticket = existing;
                }
            } finally {
                if (writing) lock.unlockWrite(stamp);
                else lock.unlockRead(stamp);
            }
        }
        if (loading) {
            liveCount.incrementAndGet();
            long shardStamp = lock.readLock();
            ResidentSection evicted = null;
            try {
                long stamp = dormantLock.writeLock();
                try {
                    section = dormant.remove(key);
                    if (section == null && !dormant.isEmpty() && dormantLimit + 100 < dormant.size() + liveCount())
                        evicted = dormant.removeFirst();
                } finally { dormantLock.unlockWrite(stamp); }
                if (section != null) { section.revive(); section.acquire(1); }
            } finally { lock.unlockRead(shardStamp); }
            if (evicted != null) evicted.releaseArray();
        } else ticket.registerWaiter();

        if (loading) {
            int status = 0;
            try {
                if (section == null) {
                    section = new ResidentSection(SectionCoordinates.level(key), SectionCoordinates.x(key),
                            SectionCoordinates.y(key), SectionCoordinates.z(key), retirement);
                    status = loader.applyAsInt(section);
                    if (status < 0) {
                        ModLog.error("World: Unable to load section " + section.contents().key + " setting to air");
                        status = 1;
                    }
                    if (status == 1) Arrays.fill(section.contents().blocks(), VoxelBits.compose((byte) 15, 0, 0));
                    section.acquire(1);
                }
                ticket.publish(section);
            } catch (RuntimeException exception) {
                ticket.fail(exception);
                long stamp = lock.writeLock();
                try { cache.remove(key, ticket); }
                finally { lock.unlockWrite(stamp); }
                liveCount.decrementAndGet();
                if (section != null) section.releaseArray();
                throw exception;
            }
            if (nullOnEmpty && status == 1) { section.release(); return null; }
            return section;
        }
        section = ticket.await();
        return section != null ? section : acquire(key, nullOnEmpty);
    }

    private void tryUnload(ResidentSection section, int hints) {
        if (activity != null) activity.run();
        if (section.shouldSave() && save != null) {
            if (section.tryAcquire()) {
                VarHandle.loadLoadFence();
                if (section.shouldSave()) {
                    if (!save.test(section, false)) {
                        ModLog.info("World: Section was concurrently admitted to the save queue");
                        section.release(true, hints);
                    } else return;
                } else {
                    ModLog.warn("World: Concurrent section admission won the save-queue race");
                    section.release(true, hints);
                }
            } else {
                if (section.shouldSave()) ModLog.error("World: Dirty section could not be acquired for saving");
                else ModLog.info("World: Section ownership changed during cache acquisition");
            }
            return;
        }
        if (section.referenceCount() != 0) return;
        int index = slice(section.contents().key);
        var cache = live[index]; var lock = locks[index];
        ResidentSection removed = null;
        long stamp = lock.writeLock();
        boolean retry = false;
        ResidentSection evicted = null;
        try {
            if (section.referenceCount() != 0) return;
            VarHandle.loadLoadFence();
            if (save != null && section.shouldSave()) {
                if (section.tryAcquire()) {
                    if (!save.test(section, true)) { retry = true; section.release(false, hints); }
                } else throw new IllegalStateException("TerraVista World: Section was dirty but is also unloaded, this is very bad");
            }
            if (!retry && section.referenceCount() == 0 && section.tryRetire()) {
                var cached = cache.remove(section.contents().key);
                var found = cached.section;
                if (found == null)
                    throw new IllegalStateException("TerraVista World: Section cache lost its resident entry: " + SectionCoordinates.describe(section.contents().key) + " object=" + System.identityHashCode(section));
                if (found != section)
                    throw new IllegalStateException("TerraVista World: Removed section not the same as the referenced section in the cache: cached: " + found + " got: " + section + " A: " + ResidentSection.REFERENCES.get(found) + " B: " + ResidentSection.REFERENCES.get(section));
                removed = section;
            }
            if (removed != null) {
                long dormantStamp = dormantLock.writeLock();
                lock.unlockWrite(stamp);
                stamp = 0;
                try {
                    var previous = dormant.put(section.contents().key, section);
                    if (previous != null) throw new IllegalStateException("TerraVista World: Duplicate resident section in cache");
                    if (dormantLimit < dormant.size()) evicted = dormant.removeFirst();
                } finally { dormantLock.unlockWrite(dormantStamp); }
            }
        } finally { if (stamp != 0) lock.unlockWrite(stamp); }
        if (retry) { tryUnload(section, hints); return; }
        if (evicted != null) evicted.releaseArray();
        if (removed != null) liveCount.decrementAndGet();
    }
    private int slice(long key) { return (int) (sliceHash(key) & (live.length - 1)); }
    public static long sliceHash(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }
    public int liveCount() { return liveCount.get(); }
    public int dormantCount() { return dormant.size(); }
}

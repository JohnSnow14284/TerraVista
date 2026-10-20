package dev.terravista.render.tree;

import dev.terravista.world.ResidentSection;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelWorld;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** 通过十六个订阅分片分发首轮网格、子节点与重建通知。 */
public final class SectionSubscriptions implements SectionWatch {
    private static final int SHARDS = 16;
    private static final class Shard {
        final Long2ByteOpenHashMap types = new Long2ByteOpenHashMap();
        final StampedLock lock = new StampedLock();
    }
    private final Shard[] shards = new Shard[SHARDS];
    private LongConsumer initial;
    private LongConsumer remesh;
    private Consumer<ResidentSection> childChanged;

    public SectionSubscriptions() { for (int i = 0; i < shards.length; i++) shards[i] = new Shard(); }
    public void callbacks(LongConsumer initial, LongConsumer remesh, Consumer<ResidentSection> childChanged) {
        if (this.remesh != null) throw new IllegalStateException();
        this.initial = initial; this.remesh = remesh; this.childChanged = childChanged;
    }
    private Shard shard(long position) {
        position = (position ^ position >>> 30) * -4658895280553007687L;
        position = (position ^ position >>> 27) * -7723592293110705685L;
        return shards[(int) ((position ^ position >>> 31) & (SHARDS - 1))];
    }
    @Override public boolean watch(long position, int types) {
        var shard = shard(position);
        var map = shard.types;
        var lock = shard.lock;
        byte delta = 0;
        long stamp = lock.readLock();
        byte current = map.getOrDefault(position, (byte) 0);
        delta = (byte) (current & types);
        current |= (byte) types;
        delta ^= (byte) (current & types);
        if (delta != 0) {
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp == 0) {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                current = map.getOrDefault(position, (byte) 0);
                delta = (byte) (current & types);
                current |= (byte) types;
                delta ^= (byte) (current & types);
                if (delta != 0) map.put(position, current);
            } else {
                stamp = writeStamp;
                map.put(position, current);
            }
        }
        lock.unlock(stamp);
        if (((delta & types) & VoxelWorld.BLOCKS_CHANGED) != 0) initial.accept(position);
        return delta != 0;
    }
    @Override public boolean unwatch(long position, int types) {
        var shard = shard(position);
        var map = shard.types;
        var lock = shard.lock;
        long stamp = lock.readLock();
        byte current = map.getOrDefault(position, (byte) 0);
        if (current == 0) throw new IllegalStateException("TerraVista Render: Section pos not in map " + SectionCoordinates.describe(position));
        boolean removed = false;
        if ((current & types) != 0) {
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp == 0) {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                current = map.getOrDefault(position, (byte) 0);
                if (current == 0) throw new IllegalStateException("TerraVista Render: Section pos not in map " + SectionCoordinates.describe(position));
            } else stamp = writeStamp;
            if ((current & types) != 0) {
                current &= (byte) ~types;
                if (current == 0) { map.remove(position); removed = true; }
                else map.put(position, current);
            }
        }
        lock.unlock(stamp);
        return removed;
    }
    @Override public int get(long position) {
        var shard = shard(position);
        long stamp = shard.lock.readLock();
        int result = shard.types.getOrDefault(position, (byte) 0);
        shard.lock.unlockRead(stamp);
        return result;
    }
    public void forward(ResidentSection section, int type) {
        long position = section.contents().key;
        var shard = shard(position);
        long stamp = shard.lock.readLock();
        byte types = (byte) (shard.types.getOrDefault(position, (byte) 0) & type);
        shard.lock.unlockRead(stamp);
        if (types != 0) {
            if ((types & VoxelWorld.CHILDREN_CHANGED) != 0) childChanged.accept(section);
            if ((types & VoxelWorld.BLOCKS_CHANGED) != 0) remesh.accept(section.contents().key);
        }
    }
    public void requestRemesh(long position) {
        var shard = shard(position);
        long stamp = shard.lock.readLock();
        byte types = shard.types.getOrDefault(position, (byte) 0);
        shard.lock.unlockRead(stamp);
        if ((types & VoxelWorld.BLOCKS_CHANGED) != 0) remesh.accept(position);
    }
}

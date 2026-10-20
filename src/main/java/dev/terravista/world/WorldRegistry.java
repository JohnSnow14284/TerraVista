package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.storage.SectionStore;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 管理会话内的活动世界及身份弱缓存。 */
public final class WorldRegistry {
    private volatile boolean running = true;
    private final StampedLock lock = new StampedLock();
    private final HashMap<WorldIdentity, VoxelWorld> active = new HashMap<>();
    private final Function<WorldIdentity, SectionStore> storage;
    private final SectionSaveQueue saves;
    private final WorldSession session;

    public WorldRegistry(Function<WorldIdentity, SectionStore> storage, SectionSaveQueue saves) {
        this(storage, saves, null);
    }
    WorldRegistry(Function<WorldIdentity, SectionStore> storage, SectionSaveQueue saves, WorldSession session) {
        this.storage = storage; this.saves = saves; this.session = session;
    }
    public WorldSession session() { return session; }
    public boolean running() { return running; }
    public void stopAdmission() { running = false; }
    public VoxelWorld find(WorldIdentity identity) {
        if (!running) return null;
        var cache = identity.cachedWorld;
        VoxelWorld world;
        if (cache == null) world = null;
        else {
            world = cache.get();
            if (world == null) identity.cachedWorld = null;
            else if (world.live()) {
                if (world.registry != this)
                    throw new IllegalStateException("TerraVista World: World cannot be in identifier cache, alive and not part of this instance");
            } else { identity.cachedWorld = null; world = null; }
        }
        if (world == null) {
            long stamp = lock.readLock();
            try { world = active.get(identity); }
            finally { lock.unlockRead(stamp); }
            if (world != null) identity.cachedWorld = new WeakReference<>(world);
        }
        if (world != null) world.markActive();
        return world;
    }
    public VoxelWorld getOrCreate(WorldIdentity identity) { return getOrCreate(identity, false); }
    public VoxelWorld getOrCreate(WorldIdentity identity, boolean acquireRef) {
        if (!running) {
            ModLog.error("World: Tried getting world object on TerraVista instance but its not running");
            return null;
        }
        var world = find(identity);
        if (world != null) {
            world.markActive();
            if (acquireRef) world.acquireRef();
            return world;
        }
        long stamp = lock.writeLock();
        try {
            if (!running) {
                ModLog.error("World: Tried getting world object on TerraVista instance but its not running");
                return null;
            }
            world = active.get(identity);
            if (world == null) world = create(identity);
            world.markActive();
            if (acquireRef) world.acquireRef();
        } finally {
            lock.unlockWrite(stamp);
        }
        identity.cachedWorld = new WeakReference<>(world);
        return world;
    }
    private VoxelWorld create(WorldIdentity identity) {
        if (!running) throw new IllegalStateException("TerraVista World: Cannot create world while not running");
        if (active.containsKey(identity)) throw new IllegalStateException("TerraVista World: Existing world with identifier");
        ModLog.info("World: Creating new world engine: " + identity.longHash() + "@" + System.identityHashCode(this));
        var world = new VoxelWorld(storage.apply(identity), this);
        world.saver(saves::enqueue);
        active.put(identity, world);
        return world;
    }
    public void cleanIdle() {
        List<WorldIdentity> idle = null;
        {
            long stamp = lock.readLock();
            try {
                for (var entry : active.entrySet()) if (entry.getValue().idle()) {
                    if (idle == null) idle = new ArrayList<>();
                    idle.add(entry.getKey());
                }
            } finally { lock.unlockRead(stamp); }
        }
        if (idle != null) {
            long stamp = lock.writeLock();
            try {
                for (var identity : idle) {
                    var world = active.get(identity);
                    if (world == null || !world.idle()) continue;
                    ModLog.info("World: Shutting down idle world: " + identity.longHash());
                    world.free();
                    active.remove(identity);
                }
            } finally { lock.unlockWrite(stamp); }
        }
    }

    void cancelImports(Consumer<VoxelWorld> cancel) {
        List<VoxelWorld> snapshot;
        long stamp = lock.readLock();
        try { snapshot = new ArrayList<>(active.values()); }
        finally { lock.unlockRead(stamp); }
        for (var world : snapshot) cancel.accept(world);
    }
    String activeSectionCounts() {
        long stamp = lock.readLock();
        try { return active.values().stream().map(world -> "" + world.activeSections()).collect(Collectors.joining(", ")); }
        finally { lock.unlockRead(stamp); }
    }
    void finishShutdown(Runnable stopWorkers, Runnable checkSaves) {
        long stamp = lock.writeLock();
        try {
            for (var entry : new ArrayList<>(active.entrySet())) {
                var world = entry.getValue();
                if (world.used()) {
                    lock.unlockWrite(stamp);
                    stamp = 0;
                    while (world.used()) {
                        checkSaves.run();
                        try { Thread.sleep(10); }
                        catch (InterruptedException exception) { throw new RuntimeException(exception); }
                    }
                    stamp = lock.writeLock();
                }
                checkSaves.run();
                world.free();
                active.remove(entry.getKey(), world);
            }
            stopWorkers.run();
            ModLog.info("World: World session shutdown complete");
        } finally {
            if (stamp != 0) lock.unlockWrite(stamp);
        }
    }
}

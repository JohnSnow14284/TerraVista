package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.TrackedResource;
import dev.terravista.storage.SectionStore;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** 管理单个世界的调色板、驻留与持久化生命周期。 */
public final class VoxelWorld {
    public static final int MAX_LEVEL = 4;
    public static final int BLOCKS_CHANGED = 1;
    public static final int CHILDREN_CHANGED = 2;
    public static final int SKIP_SAVE = 4;
    public static final int DEFAULT_CHANGES = BLOCKS_CHANGED | CHILDREN_CHANGED;

    @FunctionalInterface
    public interface ChangeListener { void changed(ResidentSection section, int flags, int neighbors); }
    @FunctionalInterface
    public interface SaveRequest { boolean save(VoxelWorld world, ResidentSection section, boolean nonBlocking, boolean acquired); }

    private final TrackedResource lifetime = TrackedResource.forReferent(this);
    public final SectionStore storage;
    public final WorldRegistry registry;
    private final StatePalette palette;
    private final SectionResidency residency;
    private volatile ChangeListener primaryListener;
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private SaveRequest saver;
    volatile boolean live = true;
    private final AtomicInteger references = new AtomicInteger();
    volatile long lastActive = System.currentTimeMillis();

    public VoxelWorld(SectionStore storage) { this(storage, null); }
    public VoxelWorld(SectionStore storage, WorldRegistry registry) {
        this.registry = registry;
        int cacheSize = 1024;
        if (Runtime.getRuntime().maxMemory() >= (1L << 32) - (200L << 20)) cacheSize = 2048;
        this.storage = storage;
        palette = new StatePalette(storage);
        residency = new SectionResidency(6, section -> storage.load(section.contents()), cacheSize,
                () -> lastActive = System.currentTimeMillis(),
                (section, nonBlocking) -> requestSave(section, nonBlocking, true));
    }
    public StatePalette palette() { return palette; }
    public boolean live() { return live; }
    public void primaryListener(ChangeListener listener) { primaryListener = listener; }
    public void addListener(ChangeListener listener) { listeners.addIfAbsent(listener); }
    public void removeListener(ChangeListener listener) { listeners.remove(listener); }
    public void saver(SaveRequest saver) { this.saver = saver; }

    public ResidentSection acquire(int level, int x, int y, int z) {
        if (!live) throw new IllegalStateException("TerraVista World: World is not live");
        return residency.acquire(level, x, y, z, false);
    }
    public ResidentSection acquireExisting(int level, int x, int y, int z) {
        if (!live) throw new IllegalStateException("TerraVista World: World is not live");
        return residency.acquire(level, x, y, z, true);
    }
    public ResidentSection acquire(long key) {
        if (!live) throw new IllegalStateException("TerraVista World: World is not live");
        return residency.acquire(key, false);
    }
    public ResidentSection acquireExisting(long key) {
        if (!live) throw new IllegalStateException("TerraVista World: World is not live");
        return residency.acquire(key, true);
    }
    public void changed(ResidentSection section) { changed(section, DEFAULT_CHANGES, 0); }
    public void changed(ResidentSection section, int flags, int neighbors) {
        if (!live) throw new IllegalStateException("TerraVista World: World is not live");
        if (!residency.owns(section)) throw new IllegalStateException("TerraVista World: Section is not from here");
        if ((flags & SKIP_SAVE) == 0) section.markDirty();
        else section.markRenderDirty();
        ChangeListener callback = primaryListener;
        if (callback != null) callback.changed(section, flags, neighbors);
        for (ChangeListener listener : listeners) listener.changed(section, flags, neighbors);
    }
    public void debug(List<String> lines) { lines.add("TerraVista sections active/cached: " + residency.liveCount() + "/" + residency.dormantCount()); }
    public int activeSections() { return residency.liveCount(); }

    public void free() {
        if (!live) throw new IllegalStateException();
        live = false;
        VarHandle.fullFence();
        if (residency.liveCount() != 0) throw new IllegalStateException();
        lifetime.free();
        // 由存储管理调色板持久化资源的释放。
        try { storage.flush(); } catch (Exception exception) { ModLog.error(exception); }
        try { storage.close(); } catch (Exception exception) { ModLog.error(exception); }
    }
    public boolean used() {
        if (!live) throw new IllegalStateException();
        return references.get() != 0 || residency.liveCount() != 0;
    }
    public boolean idle() {
        if (used()) {
            lastActive = System.currentTimeMillis();
            VarHandle.fullFence();
            return false;
        }
        return 10_000 < (System.currentTimeMillis() - lastActive);
    }
    public void markActive() {
        if (!live) throw new IllegalStateException();
        lastActive = System.currentTimeMillis();
    }
    public void acquireRef() {
        if (!live) throw new IllegalStateException();
        references.incrementAndGet();
        lastActive = System.currentTimeMillis();
    }
    public void releaseRef() {
        if (!live) throw new IllegalStateException();
        if (references.decrementAndGet() < 0) throw new IllegalStateException("TerraVista World: ref count less than 0");
        lastActive = System.currentTimeMillis();
    }
    public boolean requestSave(ResidentSection section) { return requestSave(section, false, false); }
    public boolean requestSave(ResidentSection section, boolean nonBlocking, boolean acquired) {
        if (saver != null) return saver.save(this, section, nonBlocking, acquired);
        return false;
    }
}

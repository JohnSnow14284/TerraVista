package dev.terravista.world;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.ObjIntConsumer;

/** 管理内存区块的引用、脏标记、保存与渲染生命周期。 */
public final class ResidentSection {
    public static int POSSIBLE_REUSE = 1;
    static final VarHandle REFERENCES;
    private static final VarHandle QUEUED;
    private static final VarHandle DIRTY;
    static {
        try {
            REFERENCES = MethodHandles.lookup().findVarHandle(ResidentSection.class, "references", int.class);
            QUEUED = MethodHandles.lookup().findVarHandle(ResidentSection.class, "queued", boolean.class);
            DIRTY = MethodHandles.lookup().findVarHandle(ResidentSection.class, "dirty", boolean.class);
        } catch (NoSuchFieldException | IllegalAccessException exception) { throw new RuntimeException(exception); }
    }
    private final SectionContents contents;
    final ObjIntConsumer<ResidentSection> retirement;
    private final SectionRevision revision = new SectionRevision();
    private volatile int references = 1;
    private volatile boolean queued;
    private volatile boolean dirty;

    ResidentSection(int level, int x, int y, int z, ObjIntConsumer<ResidentSection> retirement) {
        this.retirement = retirement;
        contents = new SectionContents(level, x, y, z, SectionArrayPool.take());
    }
    public static ResidentSection untracked(int level, int x, int y, int z) {
        return new ResidentSection(level, x, y, z, null);
    }
    public SectionContents contents() { return contents; }
    @Override public int hashCode() {
        return ((contents.x * 1235641 + contents.y) * 8127451 + contents.z) * 918267913 + contents.level;
    }
    void revive() { REFERENCES.set(this, 1); }
    public boolean tryAcquire() {
        int previous, next;
        do {
            previous = (int) REFERENCES.get(this);
            if ((previous & 1) == 0) return false;
            next = previous + 2;
        } while (!REFERENCES.compareAndSet(this, previous, next));
        return (next & 1) != 0;
    }
    public int acquire() { return acquire(1); }
    public int acquire(int count) {
        int state = ((int) REFERENCES.getAndAdd(this, count << 1)) + (count << 1);
        if ((state & 1) == 0)
            throw new IllegalStateException("TerraVista World: Tried to acquire unloaded section: " + SectionCoordinates.describe(contents.key) + " obj: " + System.identityHashCode(this));
        return state >> 1;
    }
    public int referenceCount() { return ((int) REFERENCES.get(this)) >> 1; }
    public int release() { return release(true, 0); }
    public int release(int hints) { return release(true, hints); }
    int release(boolean unload, int hints) {
        int state = ((int) REFERENCES.getAndAdd(this, -2)) - 2;
        if (state < 1) throw new IllegalStateException("TerraVista World: Section got into an invalid state");
        if ((state & 1) == 0) throw new IllegalStateException("TerraVista World: Tried releasing a freed section");
        if ((state >> 1) == 0 && unload) {
            if (retirement != null) retirement.accept(this, hints);
            else if (tryRetire()) releaseArray();
        }
        return state >> 1;
    }
    boolean tryRetire() {
        int witness = (int) REFERENCES.compareAndExchange(this, 1, 0);
        if ((witness & 1) == 0 && witness != 0)
            throw new IllegalStateException("TerraVista World: Section marked as free but has refs");
        if (witness == 1 && (dirty || queued))
            throw new IllegalStateException("TerraVista World: Section freed while marked as dirty or in the save queue: " + (dirty ? "dirty, " : "") + (queued ? "saveQueue" : ""));
        return witness == 1;
    }
    void releaseArray() {
        SectionArrayPool.recycle(contents);
        contents.releaseBlocks();
    }
    public boolean retired() { return (((int) REFERENCES.get(this)) & 1) == 0; }
    public long[] copyBlocks() { return contents.copy(); }
    public void copyBlocksTo(long[] target) { copyBlocksTo(target, 0); }
    public void copyBlocksTo(long[] target, int offset) { contents.copyTo(target, offset); }
    public void markDirty() { DIRTY.getAndSet(this, true); markRenderDirty(); }
    void markRenderDirty() { revision.advance(); }
    public long renderRevision() { return revision.current(); }
    public boolean exchangeQueued(boolean state) {
        return ((boolean) QUEUED.compareAndExchange(this, !state, state)) == !state;
    }
    public boolean clearDirty() { return (boolean) DIRTY.getAndSet(this, false); }
    void restoreDirty() { DIRTY.setVolatile(this, true); }
    public boolean shouldSave() { return dirty && !queued; }
}

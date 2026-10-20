package dev.terravista.render.tree;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** 原子移交完整结果并复用两个结果缓存对象。 */
final class TreePublishQueue {
    private static final VarHandle PUBLISHED, FIRST, SECOND;
    static {
        try {
            var lookup = MethodHandles.lookup();
            PUBLISHED = lookup.findVarHandle(TreePublishQueue.class, "published", TreeDelta.class);
            FIRST = lookup.findVarHandle(TreePublishQueue.class, "first", TreeDelta.class);
            SECOND = lookup.findVarHandle(TreePublishQueue.class, "second", TreeDelta.class);
        } catch (NoSuchFieldException | IllegalAccessException failure) {
            throw new RuntimeException(failure);
        }
    }
    private volatile TreeDelta published;
    private volatile TreeDelta first = new TreeDelta(), second = new TreeDelta();

    TreeDelta acquire() {
        var result = (TreeDelta) FIRST.getAndSet(this, null);
        if (result == null) result = (TreeDelta) SECOND.getAndSet(this, null);
        if (result == null) throw new IllegalStateException("TerraVista Render: There should always be an object in the result set cache pair");
        result.reset();
        return result;
    }
    boolean hasPublished() { return PUBLISHED.get(this) != null; }
    TreeDelta takePublished() { return (TreeDelta) PUBLISHED.getAndSet(this, null); }
    void publish(TreeDelta result) {
        if (!PUBLISHED.compareAndSet(this, null, result)) throw new IllegalArgumentException("TerraVista Render: Should always have null");
    }
    void recycle(TreeDelta result) {
        if (!FIRST.compareAndSet(this, null, result))
            if (!SECOND.compareAndSet(this, null, result)) throw new IllegalStateException("TerraVista Render: Could not insert result into cache");
    }
    void close() {
        if (PUBLISHED.get(this) != null) takePublished().close();
        if (FIRST.get(this) != null) ((TreeDelta) FIRST.getAndSet(this, null)).close();
        if (SECOND.get(this) != null) ((TreeDelta) SECOND.getAndSet(this, null)).close();
    }
}

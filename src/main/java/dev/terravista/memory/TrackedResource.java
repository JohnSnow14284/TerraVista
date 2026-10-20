package dev.terravista.memory;

import dev.terravista.bootstrap.ModLog;
import java.lang.ref.Cleaner;

/** 记录资源释放状态并检测泄漏。 */
public abstract class TrackedResource {
    private final ReleaseState state;

    protected TrackedResource() { this(true); }
    protected TrackedResource(boolean track) { state = watch(track, this); }
    protected TrackedResource(Object referent, boolean track) { state = watch(track, referent); }
    protected void markReleased() {
        if (isReleased()) throw new IllegalStateException("TerraVista Memory: Object " + this + " was double freed.");
        state.released()[0] = true;
        if (state.cleanable() != null) state.cleanable().clean();
    }
    public abstract void free();
    public void requireLive() {
        if (isReleased()) throw new IllegalStateException("TerraVista Memory: Object " + this + " should not be free, but is");
    }
    public boolean isReleased() { return state.released()[0]; }
    public record ReleaseState(Cleaner.Cleanable cleanable, boolean[] released) {}
    public static ReleaseState watch(boolean track, Object referent) {
        boolean[] released = new boolean[1];
        Cleaner.Cleanable cleanable = null;
        if (track) {
            String type = referent.getClass().getName();
            cleanable = NativeCleaner.INSTANCE.register(referent, () -> {
                if (!released[0]) ModLog.error("Memory: Resource was not released: " + type);
            });
        }
        return new ReleaseState(cleanable, released);
    }
    private static final class ReferentOwner extends TrackedResource {
        ReferentOwner(Object referent) { super(referent, true); }
        @Override public void free() { markReleased(); }
    }
    public static TrackedResource forReferent(Object referent) { return new ReferentOwner(referent); }
}

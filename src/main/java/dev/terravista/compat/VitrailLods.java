package dev.terravista.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Draws packed geometry inside Vitrail's own distant targets and depth publication. */
public final class VitrailLods {
    public enum Mode { NONE, PACKED, FALLBACK }
    public interface Draw { void record(RenderPass pass); }
    private static Draw current;
    private static boolean emitted;
    private static final Supplier<String> GAP = () -> "TerraVista packed LOD recording";

    public static boolean active() {
        if (!VitrailPackedPrograms.ENABLED) return false;
        try { return (boolean) Api.DRAWING.invokeExact(); }
        catch (Throwable failure) { throw failure(failure); }
    }

    public static Mode mode() {
        if (!active()) return Mode.NONE;
        try {
            Object renderer = Api.CURRENT.invoke();
            // Prefetch publishes its program map through the volatile read flag. A warming pack
            // keeps the packed path until that publication has settled which programs it serves.
            if (renderer == null || !(boolean) Api.READ.invoke(renderer)) return Mode.PACKED;
            Map<?, ?> programs = (Map<?, ?>) Api.PROGRAMS.invoke(renderer);
            Set<?> refused = (Set<?>) Api.REFUSED.invoke(renderer);
            return (boolean) Api.BROKEN.invoke(renderer) || !programs.containsKey("distant")
                    || !programs.containsKey("distant_water") || refused.contains("prepare:distant")
                    || refused.contains("prepare:distant_water")
                    || refused.contains("dropped:prepare:distant_water") ? Mode.FALLBACK : Mode.PACKED;
        } catch (Throwable failure) { throw failure(failure); }
    }

    public static void suspend() {
        if (!VitrailPackedPrograms.ENABLED) return;
        try {
            Api.SUSPEND.invokeExact();
            Api.FLUSH.invokeExact(GAP);
        } catch (Throwable failure) { throw failure(failure); }
    }

    public static boolean recording() { return current != null; }

    public static void emit(RenderPass pass) {
        if (current == null) return;
        if (emitted) throw new IllegalStateException("TerraVista: duplicate packed LOD submission");
        emitted = true;
        current.record(pass);
    }

    public static boolean draw(boolean water, Draw draw) {
        if (current != null) throw new IllegalStateException("TerraVista: nested packed LOD submission");
        try {
            Object renderer = Api.CURRENT.invoke();
            if (renderer == null) return false;
            current = draw;
            emitted = false;
            // Empty DH sections: the Mixin submits the packed indirect commands after bind and
            // skips DH's corner mapping. No stand-in DH meshes or per-section CPU iteration.
            boolean result = (boolean) Api.RECORD.invoke(renderer, RenderSystem.getDevice(),
                    Minecraft.getInstance(), water ? Api.WATER : Api.OPAQUE, List.of());
            if (result && !emitted) {
                // Upstream also returns true when a just-refused program keeps DH's own draw.
                // We have no DH mesh: select our basic path on the next frame instead.
                if (mode() == Mode.FALLBACK) return false;
                throw new IllegalStateException("TerraVista: Vitrail draw hook did not run");
            }
            return result;
        } catch (Throwable failure) {
            if (current != null) {
                current = null;
                try { suspend(); }
                catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure(failure);
        } finally {
            if (current != null) { current = null; suspend(); }
        }
    }

    private static RuntimeException failure(Throwable cause) {
        if (cause instanceof Error error) throw error;
        return cause instanceof RuntimeException runtime ? runtime
                : new IllegalStateException("TerraVista: incompatible Vitrail distant rendering API", cause);
    }

    private static final class Api {
        private static final MethodHandle DRAWING, CURRENT, RECORD, SUSPEND, FLUSH, READ, PROGRAMS, BROKEN, REFUSED;
        private static final Object OPAQUE, WATER;
        static {
            try {
                var loader = VitrailLods.class.getClassLoader();
                var chain = Class.forName("dev.vitrail.render.PackChain", false, loader);
                var distant = Class.forName("dev.vitrail.render.DistantDraw", true, loader);
                var element = Class.forName("dev.vitrail.render.DistantDraw$Element", false, loader);
                var level = Class.forName("dev.vitrail.render.LevelPass", false, loader);
                var hold = Class.forName("dev.vitrail.render.GeometryHold", false, loader);
                var lookup = MethodHandles.lookup();
                var distantLookup = MethodHandles.privateLookupIn(distant, lookup);
                DRAWING = MethodHandles.publicLookup().findStatic(chain, "drawingPack", MethodType.methodType(boolean.class));
                CURRENT = MethodHandles.privateLookupIn(chain, lookup).findStatic(chain, "distant", MethodType.methodType(distant));
                RECORD = distantLookup.findVirtual(distant, "record", MethodType.methodType(boolean.class,
                        GpuDevice.class, Minecraft.class, element, List.class));
                READ = distantLookup.findGetter(distant, "read", boolean.class);
                PROGRAMS = distantLookup.findGetter(distant, "programs", Map.class);
                BROKEN = distantLookup.findGetter(distant, "broken", boolean.class);
                REFUSED = distantLookup.findGetter(distant, "refused", Set.class);
                var elements = (Map<?, ?>) distantLookup.findStaticGetter(distant, "ELEMENTS", Map.class).invoke();
                OPAQUE = elements.get("distant");
                WATER = elements.get("distant_water");
                if (OPAQUE == null || WATER == null) throw new IllegalStateException("Vitrail distant elements missing");
                SUSPEND = MethodHandles.publicLookup().findStatic(level, "suspendCurrent", MethodType.methodType(void.class));
                FLUSH = MethodHandles.publicLookup().findStatic(hold, "flush", MethodType.methodType(void.class, Supplier.class));
            } catch (Throwable failure) { throw failure(failure); }
        }
    }

    private VitrailLods() {}
}

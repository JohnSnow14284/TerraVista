package dev.terravista.compat;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.terravista.bootstrap.RuntimeEnvironment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

/** Keeps replacement vanilla passes visible to Vitrail's suspend/resume machinery. */
public final class VitrailPasses {
    private static final MethodHandle OPEN = findOpen();

    private static MethodHandle findOpen() {
        if (!RuntimeEnvironment.modLoaded("vitrail")) return null;
        try {
            return resolve(VitrailPasses.class.getClassLoader());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TerraVista: incompatible Vitrail LevelPass API", failure);
        }
    }

    static MethodHandle resolve(ClassLoader loader) throws ReflectiveOperationException {
        var type = Class.forName("dev.vitrail.render.LevelPass", false, loader);
        return MethodHandles.publicLookup().findStatic(type, "open", MethodType.methodType(
                RenderPass.class, RenderPass.class, GpuTextureView.class,
                GpuTextureView.class, Supplier.class));
    }

    public static RenderPass open(GpuTextureView colour, GpuTextureView depth,
                                  Supplier<RenderPass> reopen) {
        // Resolve the optional ABI before acquiring a host render pass.
        return wrap(OPEN, reopen.get(), colour, depth, reopen);
    }

    static RenderPass wrap(MethodHandle open, RenderPass first, GpuTextureView colour,
                           GpuTextureView depth, Supplier<RenderPass> reopen) {
        if (open == null) return first;
        try {
            return (RenderPass) open.invokeExact(first, colour, depth, reopen);
        } catch (Throwable failure) {
            try { first.close(); }
            catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException exception) throw exception;
            throw new IllegalStateException("TerraVista: could not register Vitrail level pass", failure);
        }
    }

    private VitrailPasses() {}
}

package dev.terravista.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the installed pack's material table; model records keep stable registry state indices. */
public final class VitrailBlockIds {
    public static int generation() {
        try { return (int) Api.GENERATION.invokeExact(); }
        catch (Throwable failure) { throw new IllegalStateException("TerraVista: Vitrail block generation", failure); }
    }

    public static int id(BlockState state) {
        try { return (int) Api.ID.invokeExact(state); }
        catch (Throwable failure) { throw new IllegalStateException("TerraVista: Vitrail block material", failure); }
    }

    private static final class Api {
        private static final MethodHandle GENERATION, ID;
        static {
            try {
                Class<?> type = Class.forName("dev.vitrail.render.BlockStateIds", false,
                        VitrailBlockIds.class.getClassLoader());
                GENERATION = MethodHandles.publicLookup().findStatic(type, "generation", MethodType.methodType(int.class));
                ID = MethodHandles.publicLookup().findStatic(type, "id", MethodType.methodType(int.class, BlockState.class));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("TerraVista: incompatible Vitrail material API", failure);
            }
        }
    }
    private VitrailBlockIds() {}
}

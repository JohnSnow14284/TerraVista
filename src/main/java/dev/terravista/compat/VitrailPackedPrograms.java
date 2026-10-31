package dev.terravista.compat;

import dev.terravista.bootstrap.RuntimeEnvironment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/** Pack-load adaptation only; actual buffer bindings are scoped to each LOD submission. */
public final class VitrailPackedPrograms {
    // DH owns the upstream distant family when present. Do not reinterpret its vertex buffers.
    public static final boolean ENABLED = RuntimeEnvironment.modLoaded("vitrail")
            && !RuntimeEnvironment.modLoaded("distanthorizons");
    public static final String ABI = "1";
    private static final List<String> BLOCKS = List.of("TerraVistaScene", "TerraVistaQuads",
            "TerraVistaModels", "TerraVistaColours", "TerraVistaPositions");

    public static boolean distant(Object loaded) {
        try {
            return Api.INPUTS.invoke(Api.PROGRAM.invoke(loaded)).toString().equals("DISTANT");
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("TerraVista: cannot identify Vitrail shader inputs", failure);
        }
    }

    public static List<String> storage(List<String> original) {
        var combined = new ArrayList<>(original);
        for (String block : BLOCKS) if (!combined.contains(block)) combined.add(block);
        return List.copyOf(combined);
    }

    private static final class Api {
        private static final MethodHandle PROGRAM, INPUTS;
        static {
            try {
                ClassLoader loader = VitrailPackedPrograms.class.getClassLoader();
                Class<?> loaded = Class.forName("dev.vitrail.glsl.PackProgram$Loaded", false, loader);
                Class<?> program = Class.forName("dev.vitrail.glsl.ProgramTranslator$TranslatedProgram", false, loader);
                Class<?> inputs = Class.forName("dev.vitrail.glsl.VertexInputs", false, loader);
                PROGRAM = MethodHandles.publicLookup().findVirtual(loaded, "program", MethodType.methodType(program));
                INPUTS = MethodHandles.publicLookup().findVirtual(program, "inputs", MethodType.methodType(inputs));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("TerraVista: incompatible Vitrail program API", failure);
            }
        }
    }

    private VitrailPackedPrograms() {}
}

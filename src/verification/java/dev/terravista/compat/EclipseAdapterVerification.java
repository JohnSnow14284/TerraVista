package dev.terravista.compat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Guards pack ownership and the two preprocessor scopes without requiring a GPU or Vitrail. */
public final class EclipseAdapterVerification {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("terravista-eclipse-");
        try {
            Path dimensions = Files.createDirectories(root.resolve("dimensions"));
            Files.writeString(dimensions.resolve("voxy_solid.glsl"), "void voxy_emitFragment(VoxyFragmentParameters parameters) {}");
            Files.writeString(dimensions.resolve("voxy_translucent.glsl"), "vxDepthTexOpaque");
            Files.writeString(dimensions.resolve("voxy.json"), "\"opaqueDrawBuffers\": [1, 8]\n\"translucentDrawBuffers\": [16, 7, 11, 14]\n\"excludeLodsFromVanillaDepth\": true");
            require(EclipsePrograms.matches(root), "a renamed pack must still be recognized");
            Path world = Files.createDirectories(root.resolve("world-1"));
            Files.writeString(world.resolve("voxy_opaque.glsl"), "#define NETHER_SHADER");
            Files.writeString(world.resolve("voxy_translucent.glsl"), "#define NETHER_SHADER");
            List<String> original = List.of("#version 430 compatibility", "#define NETHER_SHADER", "original body");
            String vertex = String.join("\n", EclipsePrograms.adapt(root, world.resolve("dh_terrain.vsh"), original));
            require(vertex.startsWith("#version 430 compatibility\n#define NETHER_SHADER\n"), "dimension identity must survive");
            require(vertex.contains("terravistaCustomId") && vertex.contains("tvLodLight = gl_MultiTexCoord1.xy;"), "material/light contract");
            String opaque = String.join("\n", EclipsePrograms.adapt(root, world.resolve("dh_terrain.fsh"), original));
            String water = String.join("\n", EclipsePrograms.adapt(root, world.resolve("dh_water.fsh"), original));
            require(opaque.contains("/world-1/voxy_opaque.glsl") && opaque.contains("RENDERTARGETS: 1,8"), "opaque output contract");
            require(water.contains("/world-1/voxy_translucent.glsl") && water.contains("RENDERTARGETS: 16,7,11,14"), "water output contract");
            require(water.contains("uniform vec4 lightningBoltPosition;"), "lightning must retain its presence component");
            List<String> properties = EclipsePrograms.adapt(root, root.resolve("shaders.properties"), List.of("#ifdef VOXY", "vxRenderDistance", "#endif"));
            require(properties.get(0).equals("#undef TERRAVISTA") && properties.get(1).equals("#define DISTANT_HORIZONS"), "property scope must use supplied distances");
            require(properties.contains("blend.dh_water.colortex11 = off")
                    && properties.contains("blend.dh_water = SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE_MINUS_DST_ALPHA ONE"), "water attachment blending must survive the API change");
            List<String> settings = EclipsePrograms.adapt(root, root.resolve("lib/settings.glsl"), List.of("#ifdef VOXY"));
            require(settings.indexOf("#define TERRAVISTA 1") >= 0 && settings.indexOf("#define TERRAVISTA 1") < settings.lastIndexOf("#ifdef TERRAVISTA"), "GLSL scope must select TV before pack settings");
            require(settings.contains("#undef DISTANT_HORIZONS"), "the two shader contracts must never be advertised together");
            require(settings.contains("#define tvDepthTexOpaque dhDepthTex1") && settings.contains("#define tvDepthTex dhDepthTex"), "opaque/translucent depth identity");
            var projection = EclipsePrograms.adapt(root, root.resolve("lib/DistantHorizons_projections.glsl"),
                    List.of("#define dhVoxyProjection vxProj", "#define dhVoxyDepthTex1 vxDepthTexOpaque", "#ifdef VOXY_REFLECTIONS"));
            require(projection.equals(List.of("#define tvLodProjection tvProjection", "#define tvLodDepthTexOpaque tvDepthTexOpaque", "#ifdef TERRAVISTA_REFLECTIONS")), "native names must preserve intermediate aliases without recursive macros");
            require(EclipsePrograms.adapt(root, world.resolve("gbuffers_terrain.vsh"), original) == original, "near terrain source ownership");
            require(EclipsePrograms.adapt(root, root.resolve("other/dh_terrain.fsh"), original) == original, "missing Voxy entry must preserve DH");
            List<String> finalSource = List.of("#if MC_VERSION >= 260200 && !defined IEXT_ENABLED && IRIS_VERSION <= 11102", "body", "#endif", "#if OTHER_BUG", "body", "#endif");
            var adapted = EclipsePrograms.adapt(root, root.resolve("dimensions/final.fsh"), finalSource);
            require(adapted.get(0).endsWith("&& !defined TERRAVISTA_PACKED_LOD") && adapted.get(3).equals(finalSource.get(3)), "only the Iris renderer diagnostic is scoped");
            Files.writeString(dimensions.resolve("voxy.json"), "\"opaqueDrawBuffers\": [2, 9]");
            require(!EclipsePrograms.matches(root), "a changed output contract must not be silently accepted");
            System.out.println("Verified Eclipse adapter: " + checks + " checks");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
    private static void require(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}

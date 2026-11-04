package dev.terravista.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

/** Patches Eclipse's LOD shading into a TV contract carried by Vitrail's distant family. */
public final class EclipsePrograms {
    private static final Pattern WORD = Pattern.compile("\\b[A-Za-z_][A-Za-z_0-9]*\\b");
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("VOXY", "TERRAVISTA"), Map.entry("VOXY_PROGRAM", "TERRAVISTA_PROGRAM"),
            Map.entry("VoxyFragmentParameters", "TerraVistaFragmentParameters"),
            Map.entry("voxy_emitFragment", "terravista_emitFragment"),
            Map.entry("dhVoxyNearPlane", "tvNearPlane"), Map.entry("dhVoxyFarPlane", "tvFarPlane"),
            Map.entry("dhVoxyRenderDistance", "tvRenderDistance"),
            Map.entry("dhVoxyProjection", "tvLodProjection"), Map.entry("dhVoxyProjectionInverse", "tvLodProjectionInverse"),
            Map.entry("dhVoxyProjectionPrev", "tvLodPreviousProjection"),
            Map.entry("dhVoxyDepthTex", "tvLodDepthTex"), Map.entry("dhVoxyDepthTex1", "tvLodDepthTexOpaque"),
            Map.entry("vxProj", "tvProjection"), Map.entry("vxProjInv", "tvProjectionInverse"),
            Map.entry("vxProjPrev", "tvPreviousProjection"), Map.entry("vxModelView", "tvModelView"),
            Map.entry("vxModelViewInv", "tvModelViewInverse"), Map.entry("vxModelViewPrev", "tvPreviousModelView"),
            Map.entry("vxDepthTexOpaque", "tvDepthTexOpaque"), Map.entry("vxDepthTexTrans", "tvDepthTex"),
            Map.entry("vxRenderDistance", "tvRenderDistanceChunks"));
    public static boolean matches(Path root) {
        try {
            return Files.readString(root.resolve("dimensions/voxy_solid.glsl")).contains("void voxy_emitFragment(VoxyFragmentParameters parameters)")
                    && Files.readString(root.resolve("dimensions/voxy_translucent.glsl")).contains("vxDepthTexOpaque")
                    && outputContract(Files.readString(root.resolve("dimensions/voxy.json")));
        } catch (IOException missing) {
            return false;
        }
    }

    private static boolean outputContract(String source) {
        return source.contains("\"opaqueDrawBuffers\": [1, 8]")
                && source.contains("\"translucentDrawBuffers\": [16, 7, 11, 14]")
                && source.contains("\"excludeLodsFromVanillaDepth\": true");
    }

    public static List<String> adapt(Path root, Path file, List<String> original) {
        var patched = patch(root, file, original);
        var nativeSource = patched.stream().map(line -> WORD.matcher(line).replaceAll(
                match -> nativeName(match.group()))).toList();
        return nativeSource.equals(original) ? original : nativeSource;
    }

    private static String nativeName(String name) {
        return NAMES.getOrDefault(name, name.startsWith("VOXY_") ? "TERRAVISTA_" + name.substring(5) : name);
    }

    private static List<String> patch(Path root, Path file, List<String> original) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.equals("shaders.properties")) {
            // Option discovery also sees settings.glsl's Voxy symbol. Property expressions and
            // program scheduling still need Vitrail's supplied DH names, distances and planes.
            var changed = new ArrayList<>(List.of("#undef VOXY", "#define DISTANT_HORIZONS"));
            changed.addAll(original);
            // Preserve the former per-attachment blend contract, now under DH entry names.
            changed.addAll(List.of("blend.dh_terrain = off",
                    "blend.dh_water = SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE_MINUS_DST_ALPHA ONE",
                    "blend.dh_water.colortex11 = off"));
            return List.copyOf(changed);
        }
        if (relative.equals("lib/settings.glsl")) {
            // Keep Vitrail's DH program scheduling in shaders.properties, but select Eclipse's
            // Voxy shading branches and depth names inside GLSL. Never advertise both at once.
            var changed = new ArrayList<>(lines(resource("aliases.glsl")));
            changed.addAll(original);
            return List.copyOf(changed);
        }
        String name = file.getFileName().toString();
        boolean water = name.equals("dh_water.vsh") || name.equals("dh_water.fsh");
        boolean distant = water || name.equals("dh_terrain.vsh") || name.equals("dh_terrain.fsh");
        if (distant && Files.isRegularFile(file.resolveSibling(water ? "voxy_translucent.glsl" : "voxy_opaque.glsl"))) {
            if (name.endsWith(".vsh")) {
                String defines = original.stream().filter(line -> line.startsWith("#define "))
                        .reduce("", (a, b) -> a + b + "\n");
                return lines("#version 430 compatibility\n" + defines + resource("vertex.glsl"));
            }
            String entry = relative.substring(0, relative.lastIndexOf('/') + 1)
                    + (water ? "voxy_translucent.glsl" : "voxy_opaque.glsl");
            String buffers = water ? "16,7,11,14" : "1,8";
            return lines("#version 430 compatibility\n/* RENDERTARGETS: " + buffers + " */\n"
                    + resource("fragment.glsl").replace("TV_ECLIPSE_ENTRY", entry));
        }
        if (relative.equals("dimensions/voxy_solid.glsl") || relative.equals("dimensions/voxy_translucent.glsl")) {
            // Voxy normally emits this block from voxy.json. The Vulkan translator needs the
            // pack's actual declaration, including its feature-dependent layout, before use.
            var changed = new ArrayList<String>();
            for (String line : original) {
                changed.add(line);
                if (line.equals("#include \"/lib/settings.glsl\"")) changed.add("#include \"/lib/SSBOs.glsl\"");
            }
            return List.copyOf(changed);
        }
        if (relative.equals("dimensions/final.fsh")) {
            // This diagnostic targets Iris 1.11.2's GL terrain regression. The Vulkan host has
            // neither that renderer nor its extension mod. Do not pretend the extension is loaded.
            return original.stream().map(line -> line.stripLeading().startsWith("#if ")
                    && line.contains("MC_VERSION >= 260200") && line.contains("IEXT_ENABLED")
                    ? line + " && !defined TERRAVISTA_PACKED_LOD" : line).toList();
        }
        return original;
    }

    private static List<String> lines(String source) { return List.of(source.split("\\R", -1)); }

    private static String resource(String name) {
        try (var stream = EclipsePrograms.class.getResourceAsStream("/assets/terravista/gpu/compat/eclipse/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing Eclipse adapter " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read Eclipse adapter " + name, failure);
        }
    }
    private EclipsePrograms() {}
}

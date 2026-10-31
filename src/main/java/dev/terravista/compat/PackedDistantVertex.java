package dev.terravista.compat;

import dev.terravista.render.gpu.ShaderImports;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Substitutes vertex pulling for DH attributes while keeping Vitrail's pack-facing declarations. */
public final class PackedDistantVertex {
    private static final Map<String, String> ATTRIBUTES = Map.of(
            "vPosition", "uvec3", "meta", "uint", "vColor", "vec4",
            "irisMaterial", "uint", "irisNormal", "uint");
    private static final Pattern WORD = Pattern.compile("\\b[A-Za-z_][A-Za-z_0-9]*\\b");
    private static final Pattern DEFINE = Pattern.compile("(?m)^#define\\s+([A-Za-z_][A-Za-z_0-9]*)");
    private static final Pattern MACRO = Pattern.compile("(?m)^#(?:define|ifdef|ifndef)\\s+([A-Za-z_][A-Za-z_0-9]*)");
    // The shared modules' globals are private to this prologue. A pack can use names such as MVP,
    // frameId or getLighting itself; only the five explicitly named descriptor blocks are shared.
    private static final Set<String> PRIVATE_NAMES = Set.of(
            "Quad", "Eu32v", "usesBlendedTint", "extractPos", "extractSize", "extractFluidCornerHeights",
            "extractFace", "extractStateId", "extractBiomeId", "extractLightId", "isQuadEmpty",
            "BlockModel", "extractFaceIndentation", "extractFaceSizes", "faceHasAlphaCuttout",
            "faceHasAlphaCuttoutOverride", "faceTintState", "modelHasBiomeLUT", "modelIsTranslucent",
            "modelIsShaded", "modelIsFluid", "modelData", "colourData", "positionBuffer", "quadData",
            "getLightmapUv", "getLighting", "getLoDLevel", "getLoDPosition", "swizzelDataAxis", "getFaceSize", "taaOffset", "QuadData",
            "MVP", "baseSectionPos", "frameId", "cameraSubPos", "seaSurfaceY", "worldCurveData",
            "curvePoint", "curveBounds", "DrawCommand", "makeQuadFlags", "packVec4",
            "makeRemainingAttributes", "uint2vec4RGBA", "setupQuad", "getQuadCornerPos", "getCornerUV",
            "computeDirectionalFaceTint");

    private static String module() {
        String source = ShaderImports.module("compat/distant_vertex.glsl");
        var names = new HashSet<>(PRIVATE_NAMES);
        MACRO.matcher(source).results().map(match -> match.group(1)).forEach(names::add);
        source = WORD.matcher(source).replaceAll(match -> names.contains(match.group())
                ? "tv_" + match.group() : match.group());
        StringBuilder body = new StringBuilder(source).append('\n');
        // Include-time macros must not change the shader pack's code following this prologue.
        DEFINE.matcher(source).results().map(match -> match.group(1)).distinct()
                .forEach(name -> body.append("#undef ").append(name).append('\n'));
        return body.toString();
    }

    public static List<String> adapt(List<String> head, List<String> carried) {
        if (!ATTRIBUTES.keySet().containsAll(carried) || !carried.containsAll(List.of("vPosition", "meta"))) {
            throw new IllegalArgumentException("TerraVista: incompatible Vitrail distant attributes " + carried);
        }
        var lines = new ArrayList<String>();
        lines.add(Body.TEXT);
        int declarations = 0, entrypoints = 0;
        for (String line : head) {
            String replacement = line;
            for (String name : carried) {
                if (line.equals("in " + ATTRIBUTES.get(name) + " " + name + ";")) {
                    replacement = ATTRIBUTES.get(name) + " " + name + ";";
                    declarations++;
                    break;
                }
            }
            if (line.equals("void ofDistantVertex() {")) {
                replacement = "void terravistaPackDistantVertex() {";
                entrypoints++;
            }
            lines.add(replacement);
        }
        if (declarations != carried.size() || entrypoints != 1) {
            throw new IllegalArgumentException("TerraVista: incompatible Vitrail distant vertex prologue");
        }
        lines.add("void ofDistantVertex() {");
        lines.add("TerraVistaDistantVertex tv_vertex = terravistaDistantVertex();");
        lines.add("vPosition = uvec3(0); meta = tv_extractLightId(tv_quadData[uint(gl_VertexIndex) >> 2]);");
        if (carried.contains("vColor")) lines.add("vColor = tv_vertex.colour;");
        if (carried.contains("irisNormal")) lines.add("irisNormal = tv_extractFace(tv_quadData[uint(gl_VertexIndex) >> 2]);");
        if (carried.contains("irisMaterial")) lines.add("irisMaterial = tv_vertex.material;");
        lines.add("terravistaPackDistantVertex();");
        lines.add("of_Vertex = tv_vertex.position;");
        lines.add("}");
        return List.copyOf(lines);
    }

    private PackedDistantVertex() {}

    private static final class Body {
        private static final String TEXT = module();
    }
}

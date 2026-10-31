package dev.terravista.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

/** Depth-tests LOD fragments against the real near world without publishing near depth as LOD. */
public final class VitrailFragments {
    public static final String DEPTH = "TerraVistaWorldDepth";
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)\\s*\\{");
    private static final String WRAPPER = """
            layout(std430) readonly restrict buffer TerraVistaScene {
                layout(offset = 104) vec2 terravistaWorldDepthPair;
            };
            uniform sampler2D TerraVistaWorldDepth;
            void main() {
                float world = texelFetch(TerraVistaWorldDepth, ivec2(gl_FragCoord.xy), 0).r;
                float lod = terravistaWorldDepthPair.x * gl_FragCoord.z + terravistaWorldDepthPair.y;
                if (world > 0.0 && lod <= world) discard;
                terravistaDistantFragment();
            }
            """;

    public static Object adapt(Object program) {
        if (!VitrailPackedPrograms.distantProgram(program)) return program;
        try {
            Map<?, ?> stages = (Map<?, ?>) get(program, "stages");
            var changed = new LinkedHashMap<Object, Object>(stages);
            boolean found = false;
            Object sampler = null;
            for (var stage : stages.entrySet()) {
                if (!stage.getKey().toString().equals("FRAGMENT")) continue;
                String source = (String) get(stage.getValue(), "text");
                if (source.contains("terravistaDistantFragment")) return program;
                var main = MAIN.matcher(source);
                if (!main.find()) throw new IllegalStateException("TerraVista: incompatible Vitrail fragment entry point");
                // Packs can keep both vertex and fragment mains behind preprocessor branches.
                // Rename every candidate: shaderc retains only the stage's active definition.
                source = MAIN.matcher(source).replaceAll("void terravistaDistantFragment() {") + "\n" + WRAPPER;
                Class<?> uniform = Class.forName("dev.vitrail.glsl.TranslatedUnit$Uniform", false,
                        VitrailFragments.class.getClassLoader());
                sampler = uniform.getMethod("of", String.class, String.class).invoke(null, DEPTH, "sampler2D " + DEPTH);
                changed.put(stage.getKey(), copy(stage.getValue(), Map.of("text", source,
                        "samplers", append(get(stage.getValue(), "samplers"), sampler))));
                found = true;
            }
            if (!found) throw new IllegalStateException("TerraVista: distant fragment stage missing");
            return copy(program, Map.of("stages", Map.copyOf(changed), "samplers", append(get(program, "samplers"), sampler)));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TerraVista: incompatible Vitrail fragment API", failure);
        }
    }

    public static Object depthKind() { return DepthKind.VALUE; }

    private static final class DepthKind {
        private static final Object VALUE = resolve();
        private static Object resolve() {
            try {
                return Class.forName("dev.vitrail.pack.target.SamplerPlan$Kind", false,
                        VitrailFragments.class.getClassLoader()).getField("DEPTH").get(null);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("TerraVista: incompatible Vitrail sampler API", failure);
            }
        }
    }

    private static List<?> append(Object original, Object value) {
        var values = new ArrayList<Object>((List<?>) original);
        values.add(value);
        return List.copyOf(values);
    }

    private static Object get(Object record, String name) throws ReflectiveOperationException {
        return record.getClass().getMethod(name).invoke(record);
    }

    // Called at pack load only. Preserve every record component not explicitly changed, including
    // upstream's translation notes, uniforms and future additional metadata.
    private static Object copy(Object record, Map<String, ?> changes) throws ReflectiveOperationException {
        var fields = record.getClass().getRecordComponents();
        var types = new Class<?>[fields.length];
        var values = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            types[i] = fields[i].getType();
            values[i] = changes.containsKey(fields[i].getName()) ? changes.get(fields[i].getName())
                    : fields[i].getAccessor().invoke(record);
        }
        return record.getClass().getConstructor(types).newInstance(values);
    }

    private VitrailFragments() {}
}

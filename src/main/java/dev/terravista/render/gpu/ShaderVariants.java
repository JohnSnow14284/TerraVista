package dev.terravista.render.gpu;

import dev.terravista.render.DepthConvention;

import java.util.LinkedHashMap;
import java.util.Map;

/** 按顺序准备展开源码与 Vulkan 编译宏。 */
public final class ShaderVariants {
    public static String load(ShaderProgram program, Map<String, String> defines) {
        String source = ShaderImports.expand(program);
        StringBuilder header = new StringBuilder();
        StringBuilder body = new StringBuilder();
        boolean versionSeen = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (!versionSeen && trimmed.startsWith("#version")) {
                header.append("#version 460 core\n");
                versionSeen = true;
                continue;
            }
            if (!versionSeen || trimmed.startsWith("#extension")) {
                header.append(line).append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        StringBuilder output = new StringBuilder(header);
        if (!versionSeen) output.insert(0, "#version 460 core\n");
        for (var define : defines.entrySet()) {
            output.append("#define ").append(define.getKey());
            if (define.getValue() != null && !define.getValue().isEmpty()) {
                output.append(' ').append(define.getValue());
            }
            output.append('\n');
        }
        return output.append(body).toString();
    }

    public static Defines defines() { return new Defines(); }

    public static final class Defines {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Defines define(String key) { values.put(key, ""); return this; }
        public Defines define(String key, int value) { values.put(key, Integer.toString(value)); return this; }
        public Defines define(String key, float value) { values.put(key, Float.toString(value)); return this; }
        public Defines define(String key, String value) { values.put(key, value); return this; }
        public Defines when(String key, boolean condition) { if (condition) values.put(key, ""); return this; }

        public Defines depth(DepthConvention convention) {
            when("USE_ZERO_ONE_DEPTH", convention.zeroToOne());
            when("USE_REVERSE_Z", convention.reverseZ());
            return this;
        }

        public Map<String, String> build() { return values; }
    }

    private ShaderVariants() {}
}

package dev.terravista.render.gpu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 按程序入口装配 GPU 模块并检查循环包含。 */
public final class ShaderImports {
    private static final String ROOT = "/assets/terravista/gpu/";
    private static final Pattern INCLUDE = Pattern.compile("#include\\s+\"([a-z0-9_/-]+\\.[a-z]+)\"");

    public static String expand(ShaderProgram program) {
        return "#version 460 core\n" + String.join("\n", expand(program.path, new ArrayDeque<>()));
    }

    private static List<String> expand(String path, ArrayDeque<String> chain) {
        if (chain.contains(path)) {
            throw new IllegalArgumentException("TerraVista Render: Cyclic GPU module include: " + chain + " -> " + path);
        }
        chain.addLast(path);
        try {
            List<String> output = new ArrayList<>();
            for (String line : read(path)) {
                if (line.startsWith("#version")) continue;
                if (line.startsWith("#include")) {
                    var include = INCLUDE.matcher(line);
                    if (!include.matches()) {
                        throw new IllegalArgumentException("TerraVista Render: Invalid GPU include in " + path + ": " + line);
                    }
                    output.addAll(expand(include.group(1), chain));
                } else {
                    output.add(line);
                }
            }
            return output;
        } finally {
            chain.removeLast();
        }
    }

    private static List<String> read(String path) {
        try (var stream = ShaderImports.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new IllegalStateException("TerraVista Render: GPU module is missing: " + path);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.readAllLines();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("TerraVista Render: Cannot read GPU module: " + path, failure);
        }
    }

    private ShaderImports() {}
}

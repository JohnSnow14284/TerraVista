package dev.terravista.config;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/** 管理存储构造的变量绑定与有序路径栈。 */
public final class StorageBuildContext {
    public static final String BASE_SAVE_PATH = "{base_save_path}";
    public static final String WORLD_IDENTIFIER = "{world_identifier}";
    public static final String PLAYER_UUID = "{player_uuid}";
    public static final String DEFAULT_STORAGE_PATH = BASE_SAVE_PATH + "/" + WORLD_IDENTIFIER + "/";
    private final Map<String, String> bindings = new HashMap<>();
    private final Stack<String> segments = new Stack<>();

    public StorageBuildContext bind(String token, String value) {
        if (!(token.startsWith("{") && token.endsWith("}")))
            throw new IllegalArgumentException("TerraVista Config: Storage property names must be enclosed in braces");
        bindings.put(token, value);
        return this;
    }
    public StorageBuildContext push(String segment) { segments.push(segment); return this; }
    public StorageBuildContext pop() { segments.pop(); return this; }

    private static String append(String prefix, String segment) {
        if (segment.contains("..")) throw new IllegalStateException("TerraVista Config: Relative resolving not supported");
        if (!prefix.isBlank() && !prefix.endsWith("/")) prefix += "/";
        if (segment.startsWith("/")) return segment;
        if (segment.startsWith("./")) segment = segment.substring(2);
        if (segment.startsWith(":", 1)) return segment;
        return prefix + segment;
    }
    public String path() {
        String previous;
        String resolved = "";
        do {
            previous = resolved;
            resolved = "";
            for (var segment : segments) resolved = append(resolved, segment);
        } while (!previous.equals(resolved));
        return resolved;
    }
    public String substitute(String text) {
        for (var binding : bindings.entrySet()) text = text.replace(binding.getKey(), binding.getValue());
        return text;
    }
    public String ensureDirectory(String path) {
        try { Files.createDirectories(new File(path).toPath()); }
        catch (Exception exception) { throw new RuntimeException(exception); }
        return path;
    }
}

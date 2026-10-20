package dev.terravista.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.terravista.storage.SectionStoreConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 按需读取世界存储设置并迁移已有配置。 */
public record WorldStorageSettings(boolean enabled, SectionStoreConfig pipeline) {
    private static final Gson JSON = StorageTypeRegistry.createJson();

    public static WorldStorageSettings load(Path directory, Path previousDirectory) {
        var manifest = directory.resolve("storage.json");
        var localConfig = directory.resolve("config.json");
        var source = Files.exists(manifest) ? manifest
                : Files.exists(localConfig) ? localConfig : previousDirectory.resolve("config.json");
        var defaults = new WorldStorageSettings(true, StorageTypeRegistry.defaultSerializer());
        if (!Files.exists(source)) return defaults;
        try {
            var document = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            if (source.equals(manifest)) {
                if (document.get("schema").getAsInt() != 1)
                    throw new IllegalArgumentException("Unsupported storage settings schema");
                return read(document, "enabled", "pipeline", false);
            }
            if (document.get("version").getAsInt() != 1)
                throw new IllegalArgumentException("Unsupported storage configuration version");
            var settings = read(document, "disabled", "sectionStorageConfig", true);
            if (!settings.enabled || !JSON.toJsonTree(settings.pipeline, SectionStoreConfig.class)
                    .equals(JSON.toJsonTree(defaults.pipeline, SectionStoreConfig.class))) {
                var converted = new JsonObject();
                converted.addProperty("schema", 1);
                converted.addProperty("enabled", settings.enabled);
                converted.add("pipeline", JSON.toJsonTree(settings.pipeline, SectionStoreConfig.class));
                save(manifest, converted);
            }
            if (source.equals(localConfig)) Files.delete(localConfig);
            return settings;
        } catch (Exception exception) {
            throw new IllegalStateException("TerraVista: Cannot read world storage settings: " + source, exception);
        }
    }

    private static WorldStorageSettings read(JsonObject document, String enabledKey,
                                              String pipelineKey, boolean inverted) {
        var flag = document.get(enabledKey);
        boolean enabled = flag == null ? true : flag.getAsBoolean() != inverted;
        var pipeline = JSON.fromJson(document.get(pipelineKey), SectionStoreConfig.class);
        if (pipeline == null) throw new IllegalArgumentException("Missing storage pipeline");
        return new WorldStorageSettings(enabled, pipeline);
    }

    /** 写入成功后再替换目标文件。 */
    private static void save(Path target, JsonObject document) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = Files.createTempFile(target.getParent(), "storage-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(document));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

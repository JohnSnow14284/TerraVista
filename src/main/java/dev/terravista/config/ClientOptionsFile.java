package dev.terravista.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.RuntimeEnvironment;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.neoforged.fml.loading.FMLPaths;

/** 读写 TerraVista 客户端配置并补齐默认配置。 */
public final class ClientOptionsFile {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting().excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.TRANSIENT).create();
    public static final ClientOptionsFile DEFAULT = new ClientOptionsFile(
            () -> FMLPaths.CONFIGDIR.get().resolve("terravista-config.json"),
            () -> RuntimeEnvironment.IN_MINECRAFT && !RuntimeEnvironment.DEDICATED_SERVER);
    private final Supplier<Path> path;
    private final BooleanSupplier access;

    public ClientOptionsFile(Supplier<Path> path, BooleanSupplier access) {
        this.path = path; this.access = access;
    }

    public ClientOptions loadOrCreate() {
        if (!access.getAsBoolean()) {
            return new ClientOptions();
        }
        var file = path.get();
        boolean exists = Files.exists(file);
        if (exists) {
            try (var reader = new FileReader(file.toFile())) {
                var config = GSON.fromJson(reader, ClientOptions.class);
                if (config != null) { config.normalize(); save(config); return config; }
                ModLog.error("Config: Failed to load TerraVista config, resetting");
            } catch (IOException exception) { ModLog.error("Config: Could not load config", exception); }
            catch (JsonParseException exception) { ModLog.error("Config: Could not parse config", exception); }
            ModLog.info("Config: Client configuration could not be loaded; creating defaults");
        } else ModLog.info("Config: Client configuration is absent; writing defaults");
        var config = new ClientOptions(); save(config); return config;
    }
    public synchronized void save(ClientOptions config) {
        Objects.requireNonNull(config);
        config.normalize();
        if (!access.getAsBoolean()) { ModLog.info("Config: Configuration save skipped: TerraVista is unavailable"); return; }
        Path temporary = null;
        try {
            var file = path.get().toAbsolutePath();
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "terravista-", ".tmp");
            Files.writeString(temporary, GSON.toJson(config));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) { ModLog.error("Config: Failed to write config file", exception); }
        finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { ModLog.warn("Config: Temporary configuration file could not be removed", exception); }
            }
        }
    }
}

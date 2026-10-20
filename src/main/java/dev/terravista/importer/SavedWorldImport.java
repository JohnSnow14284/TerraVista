package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.world.WorldIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeManager;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 在后台发现存档维度，按当前会话身份分别启动区域导入。 */
public final class SavedWorldImport {
    private static final AtomicBoolean discovering = new AtomicBoolean();
    private SavedWorldImport() {}

    public static void run(Path directory, Consumer<Component> failure) {
        var client = Minecraft.getInstance();
        var session = SessionLifecycle.current();
        var level = client.level;
        var server = client.getSingleplayerServer();
        if (session == null || level == null || server == null) {
            failure.accept(Component.translatable("terravista.message.singleplayer_required"));
            return;
        }
        if (!discovering.compareAndSet(false, true)) {
            failure.accept(Component.translatable("terravista.message.import_discovering"));
            return;
        }
        Thread.ofVirtual().name("TerraVista save discovery").start(() -> {
            try {
                var dimensions = SaveDirectories.find(directory);
                client.schedule(() -> {
                    discovering.set(false);
                    if (SessionLifecycle.current() != session || client.level != level) {
                        failure.accept(Component.translatable("terravista.message.import_session_changed"));
                        return;
                    }
                    for (var entry : dimensions.entrySet()) {
                        try {
                            var target = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.getKey()));
                            if (target == null) {
                                failure.accept(Component.translatable("terravista.message.import_dimension_missing", entry.getKey().toString()));
                                continue;
                            }
                            var identity = new WorldIdentity(target.dimension(), BiomeManager.obfuscateSeed(target.getSeed()),
                                    target.dimensionTypeRegistration().unwrapKey().orElse(null));
                            if (!CurrentWorldImport.start(entry.getValue().toFile(), session, target, identity))
                                failure.accept(Component.translatable("terravista.message.import_busy", entry.getKey().toString()));
                        } catch (Exception exception) {
                            ModLog.error("Import: Dimension could not be imported", entry.getKey(), exception);
                        }
                    }
                });
            } catch (Exception exception) {
                client.schedule(() -> {
                    discovering.set(false);
                    ModLog.error("Import: Selected save could not be imported", exception);
                });
            }
        });
    }
}

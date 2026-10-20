package dev.terravista.world;

import dev.terravista.compat.SodiumWorkers;
import dev.terravista.config.ClientStorageLocation;
import dev.terravista.config.WorldStorageSettings;
import dev.terravista.config.StorageBuildContext;
import dev.terravista.importer.ImportProgressUi;
import dev.terravista.storage.SectionStore;
import dev.terravista.storage.WorldStorageIdentity;
import dev.terravista.storage.StorageDirectoryMigration;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;

/** 以客户端存储、采集和进度策略装配世界会话。 */
public final class ClientWorldSession {
    private final Path basePath;
    private final Path previousPath;
    private final WorldStorageSettings options;
    private final WorldSession session;

    public ClientWorldSession() {
        basePath = ClientStorageLocation.current().normalize();
        StorageDirectoryMigration.copyIfAbsent(ClientStorageLocation.previousLod(), basePath);
        previousPath = ClientStorageLocation.previous();
        options = WorldStorageSettings.load(basePath, previousPath);
        session = new WorldSession(options::enabled, this::storage,
                ImportProgressUi::new, identity -> true);
        SodiumWorkers.update(session);
    }

    private SectionStore storage(WorldIdentity identity) {
        StorageDirectoryMigration.copyIfAbsent(previousPath
                .resolve(WorldStorageIdentity.previousDirectory(identity)).resolve("storage"),
                basePath.resolve(WorldStorageIdentity.directory(identity)));
        var context = new StorageBuildContext();
        context.bind(StorageBuildContext.BASE_SAVE_PATH, basePath.toString());
        context.bind(StorageBuildContext.WORLD_IDENTIFIER, WorldStorageIdentity.directory(identity));
        context.bind(StorageBuildContext.PLAYER_UUID,
                Minecraft.getInstance().getUser().getProfileId().toString().replace(':', '-'));
        context.push(StorageBuildContext.DEFAULT_STORAGE_PATH);
        return options.pipeline().build(context);
    }

    public Path storageBasePath() { return basePath; }
    public WorldSession session() { return session; }
    public static WorldSession open() { return new ClientWorldSession().session(); }
}

package dev.terravista.importer;

import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.world.WorldAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import dev.terravista.world.WorldSession;
import dev.terravista.world.WorldIdentity;
import net.minecraft.world.level.Level;
import java.io.File;
import java.util.function.Consumer;

/** 定位当前单人世界的维度区域并启动导入。 */
public final class CurrentWorldImport {
    private CurrentWorldImport() {}

    public static void run(ImportJob.Progress progress, Consumer<Boolean> result, Consumer<Component> failure) {
        if (SessionLifecycle.current() == null || Minecraft.getInstance().level == null) {
            failure.accept(Component.translatable("terravista.message.session_required"));
            return;
        }
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            failure.accept(Component.translatable("terravista.message.singleplayer_required"));
            return;
        }
        var region = DimensionType.getStorageFolder(Minecraft.getInstance().level.dimension(),
                server.getWorldPath(LevelResource.ROOT)).resolve("region");
        if (!region.toFile().exists() || !region.toFile().isDirectory()) {
            failure.accept(Component.translatable("terravista.message.region_missing"));
            return;
        }
        if (start(region.toFile(), progress, result)) return;
        failure.accept(Component.translatable("terravista.message.import_failed"));
        return;
    }

    private static boolean start(File directory, ImportJob.Progress progress, Consumer<Boolean> result) {
        var session = SessionLifecycle.current();
        if (session == null) return false;
        return start(directory, session, Minecraft.getInstance().level, WorldAccess.identity(Minecraft.getInstance().level), progress, result);
    }

    static boolean start(File directory, WorldSession session, Level level, WorldIdentity identity) {
        return start(directory, session, level, identity, null, null);
    }

    private static boolean start(File directory, WorldSession session, Level level, WorldIdentity identity,
                                 ImportJob.Progress progress, Consumer<Boolean> result) {
        if (identity == null) return false;
        var world = session.worlds().getOrCreate(identity);
        if (world == null) return false;
        return session.imports().makeIfAbsent(world, () -> {
            var job = new RegionImport(world, level,
                    session.scheduler(), session.savingAllowed);
            job.directory(directory);
            return job;
        }, progress, result);
    }
}

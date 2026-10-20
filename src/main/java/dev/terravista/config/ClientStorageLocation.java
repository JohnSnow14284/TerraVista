package dev.terravista.config;

import dev.terravista.bootstrap.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;

/** 按单人世界或服务器地址选择 TerraVista 存储目录。 */
public final class ClientStorageLocation {
    private ClientStorageLocation() {}

    public static Path current() {
        return lodRoot().resolve(worldFolder());
    }

    public static Path lodRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tv").resolve("lod").toAbsolutePath().normalize();
    }

    public static Path previousLod() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("tvlod").resolve(worldFolder()).toAbsolutePath().normalize();
    }

    public static Path previous() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return (server != null ? server.getWorldPath(LevelResource.ROOT).resolve("terravista")
                : Minecraft.getInstance().gameDirectory.toPath().resolve(".terravista/saves").resolve(worldFolder())).toAbsolutePath().normalize();
    }

    private static String worldFolder() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) return server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
        var mode = Minecraft.getInstance().gameMode;
        String folder;
        if (mode == null) {
            ModLog.error("Config: No network connection is available for the storage location");
            folder = "UNKNOWN";
        } else {
            var info = mode.connection.getServerData();
            if (info == null) {
                ModLog.error("Config: Server metadata is unavailable for the storage location");
                folder = "UNKNOWN";
            } else {
                folder = info.isRealm() ? "realms" : info.ip.replace(":", "_");
            }
        }
        return folder.replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}

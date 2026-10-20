package dev.terravista.config;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.client.gui.screens.Screen;
import dev.terravista.importer.SavedWorldImport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import java.nio.file.Path;
import static org.lwjgl.sdl.SDLDialog.SDL_ShowOpenFolderDialog;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.system.MemoryUtil.*;

/** 连接导入按钮与原生存档文件夹选择器。 */
public final class ImportOptions {
    private static SDL_DialogFileCallback dialog;
    private ImportOptions() {}

    public static boolean available() {
        var client = Minecraft.getInstance();
        return client.level != null && client.getSingleplayerServer() != null && SessionLifecycle.current() != null;
    }

    public static void current(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new CurrentWorldImportScreen(parent));
    }

    public static void openLodFolder() {
        var directory = ClientStorageLocation.lodRoot();
        try {
            java.nio.file.Files.createDirectories(directory);
            com.mojang.blaze3d.Blaze3D.openPath(directory);
        } catch (Exception exception) { ModLog.error("Storage: LOD folder could not be opened", exception); }
    }

    public static void chooseSave() {
        if (!available() || dialog != null) return;
        var client = Minecraft.getInstance();
        var session = SessionLifecycle.current();
        var level = client.level;
        dialog = SDL_DialogFileCallback.create((userdata, files, filter) -> {
            String selected = files == NULL || memGetAddress(files) == NULL ? null : memUTF8(memGetAddress(files));
            String error = files == NULL ? SDL_GetError() : null;
            client.schedule(() -> {
                dialog.free();
                dialog = null;
                if (error != null) { ModLog.error("Import: Folder selection failed", error); return; }
                if (selected == null) return;
                if (SessionLifecycle.current() != session || client.level != level) {
                    failure(Component.translatable("terravista.message.import_session_changed"));
                    return;
                }
                try { SavedWorldImport.run(Path.of(selected), ImportOptions::failure); }
                catch (Exception exception) { ModLog.error("Import: Selected path could not be read", exception); }
            });
        });
        try {
            SDL_ShowOpenFolderDialog(dialog, NULL, client.getWindow().handle(),
                    client.gameDirectory.toPath().resolve("saves").toAbsolutePath().toString(), false);
        } catch (Exception exception) {
            dialog.free();
            dialog = null;
            ModLog.error("Import: Folder selector could not be opened", exception);
        }
    }

    private static void failure(Component message) { ModLog.error("Import:", message.getString()); }
}

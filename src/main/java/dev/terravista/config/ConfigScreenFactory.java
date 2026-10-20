package dev.terravista.config;

import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;

/** 打开已注册配置的首个页面，未注册时返回父屏幕。 */
public final class ConfigScreenFactory {
    private ConfigScreenFactory() {}

    public static Screen create(Screen parent) {
        var options = ConfigManager.CONFIG.getModOptions().stream()
                .filter(option -> option.configId().equals("terravista")).findFirst();
        if (options.isEmpty()) return parent;
        var page = (OptionPage) options.get().pages().getFirst();
        return VideoSettingsScreen.createScreen(parent, page);
    }
}

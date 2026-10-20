package dev.terravista.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** 注册重载按键并在客户端处理按下事件。 */
public final class ClientHotkeys {
    private static KeyMapping reload;
    private ClientHotkeys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        var category = new KeyMapping.Category(Identifier.fromNamespaceAndPath("terravista", "general"));
        event.registerCategory(category);
        reload = new KeyMapping("key.terravista.reload", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYBOARD, InputConstants.KEY_F10, category);
        event.register(reload);
    }

    public static void tick(ClientTickEvent.Post event) {
        if (reload == null) return;
        while (reload.consumeClick()) {
            var client = Minecraft.getInstance();
            if (client.level != null && client.gui.screen() == null) {
                ClientReload.run(message -> client.gui.hud.getChat().addClientSystemMessage(
                        message.copy().withStyle(ChatFormatting.RED)));
            }
        }
    }
}

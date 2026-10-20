package dev.terravista.bootstrap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import java.nio.file.Path;

/** 将系统消息通知提交到客户端线程。 */
public final class ClientNotifications {
    public static void error(Component message, Path log) {
        var output = message.copy().withStyle(ChatFormatting.RED);
        if (log != null) {
            var hint = Component.translatable("terravista.message.log_hint", log.toString());
            var link = Component.translatable("terravista.message.open_log").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenFile(log)).withHoverEvent(new HoverEvent.ShowText(hint)));
            output.append(" ").append(link);
        } else output.append(" ").append(Component.translatable("terravista.message.log_unavailable"));
        post(output);
    }

    private static void post(Component message) {
        var client = Minecraft.getInstance();
        if (client != null) {
            client.executeIfPossible(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) client.gui.hud.getChat().addClientSystemMessage(message);
            });
        }
    }
}

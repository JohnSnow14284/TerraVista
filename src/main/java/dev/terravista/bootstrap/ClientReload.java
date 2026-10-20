package dev.terravista.bootstrap;

import dev.terravista.compat.RenderSessionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** 按既定顺序关闭渲染器、重建会话并刷新场景。 */
public final class ClientReload {
    private ClientReload() {}

    public static void run(Consumer<Component> failure) {
        if (SessionLifecycle.current() == null) {
            failure.accept(Component.translatable("terravista.message.session_required"));
            return;
        }
        try {
            var renderer = RenderSessionAccess.holder();
            if (renderer != null) renderer.terravista$close();
            SessionLifecycle.shutdown();
            SessionLifecycle.create();
            var extractor = Minecraft.getInstance().levelExtractor;
            if (extractor != null) extractor.allChanged();
        } catch (RuntimeException exception) {
            ModLog.error("World: Session reload failed", exception);
        }
    }
}

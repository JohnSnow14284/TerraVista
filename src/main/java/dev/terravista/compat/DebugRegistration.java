package dev.terravista.compat;

import dev.terravista.bootstrap.CommonEntrypoint;
import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import java.util.List;

/** 注册版本与远景运行状态条目。 */
public final class DebugRegistration {
    private DebugRegistration() {}

    public static void register(RegisterDebugEntriesEvent event) {
        event.register(Identifier.fromNamespaceAndPath("terravista", "version"), (lines, level, clientChunk, serverChunk) -> {
            if (!SessionLifecycle.available()) {
                lines.addLine(ChatFormatting.RED + "TerraVista " + CommonEntrypoint.version());
                return;
            }
            if (SessionLifecycle.current() == null) {
                lines.addLine(ChatFormatting.YELLOW + "TerraVista " + CommonEntrypoint.version());
                return;
            }
            lines.addLine((RenderSessionAccess.current() == null ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN)
                    + "TerraVista " + CommonEntrypoint.version());
        });
        event.register(Identifier.fromNamespaceAndPath("terravista", "debug"), new LodDebugEntry());
    }

    public static void beforeSort(boolean overlayVisible, List<Identifier> enabled) {
        if (overlayVisible) {
            var version = Identifier.fromNamespaceAndPath("terravista", "version");
            if (!enabled.contains(version)) enabled.add(version);
        }
    }
}

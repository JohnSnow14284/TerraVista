package dev.terravista.compat;

import dev.terravista.bootstrap.SessionLifecycle;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;

/** 按世界会话与渲染会话分组输出调试信息。 */
public final class LodDebugEntry implements DebugScreenEntry {
    @Override public void display(DebugScreenDisplayer lines, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        if (!SessionLifecycle.available()) return;
        var session = SessionLifecycle.current();
        if (session == null) return;
        var renderer = RenderSessionAccess.current();
        var sessionLines = new ArrayList<String>();
        session.debug(sessionLines);
        lines.addToGroup(Identifier.fromNamespaceAndPath("terravista", "instance_debug"), sessionLines);
        if (renderer != null) {
            var renderLines = new ArrayList<String>();
            renderer.debug(renderLines);
            lines.addToGroup(Identifier.fromNamespaceAndPath("terravista", "render_debug"), renderLines);
        }
    }
}

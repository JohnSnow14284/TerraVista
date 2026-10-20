package dev.terravista.render;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.bootstrap.SessionLifecycle;
import dev.terravista.compat.SodiumWorkers;
import dev.terravista.config.ClientOptions;
import dev.terravista.world.WorldAccess;
import dev.terravista.world.WorldIdentity;
import net.minecraft.world.level.Level;
import java.util.Objects;

/** 管理当前世界的渲染会话创建、切换与关闭。 */
public final class RenderActivation {
    private WorldIdentity identity;
    private RenderSession renderer;

    public RenderSession current() { return renderer; }

    public void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    public void level(Level level) {
        var next = level == null ? null : WorldAccess.identity(level);
        if (Objects.equals(identity, next)) return;
        close();
        identity = next;
    }

    public void create() {
        if (renderer != null) throw new IllegalStateException("TerraVista Render: Cannot have multiple renderers");
        if (!ClientOptions.CONFIG.renderingEnabled(SessionLifecycle::available, GraphicsMode::nativeVulkan)) {
            ModLog.info("Render: Renderer creation skipped: native Vulkan rendering is unavailable");
            return;
        }
        if (identity == null) {
            ModLog.info("Render: Renderer creation skipped: world identity is unavailable");
            return;
        }
        if (SessionLifecycle.current() == null) {
            ModLog.info("Render: Renderer creation skipped: no active session");
            return;
        }
        var world = WorldAccess.getOrCreate(identity, true);
        if (world == null) {
            ModLog.warn("Render: Renderer creation skipped: voxel world is unavailable");
            return;
        }
        var session = world.registry == null ? null : world.registry.session();
        if (session == null) throw new IllegalStateException();
        renderer = new RenderSession(world, session.scheduler());
        SodiumWorkers.update(session);
    }
}

package dev.terravista.bootstrap;

import dev.terravista.render.DistantBeacons;
import dev.terravista.render.DistantPlayers;

/** 跟踪客户端连接状态并连接世界会话的启动与退出。 */
public final class ClientLifecycleHooks {
    private static boolean connected;
    private ClientLifecycleHooks() {}

    public static void start() {
        if (connected) throw new IllegalStateException("TerraVista Startup: Cannot start new session while in a session");
        connected = true;
        try {
            if (SessionLifecycle.current() != null) SessionLifecycle.shutdown();
            if (SessionLifecycle.available()) SessionLifecycle.create();
        } catch (RuntimeException exception) { ModLog.error("World: Session startup failed", exception); }
    }

    public static void end() {
        if (!connected) throw new IllegalStateException("TerraVista Startup: Cannot end a session while not in a session");
        connected = false;
        DistantBeacons.bind(null);
        DistantPlayers.clear();
        try { SessionLifecycle.shutdown(); }
        catch (RuntimeException exception) { ModLog.error("World: Session shutdown failed", exception); }
    }

    public static void loggedIn() {
        if (!connected) start();
    }

    public static void disconnected() {
        if (connected) end();
    }
}

package dev.terravista.bootstrap;

import dev.terravista.world.WorldSession;
import java.util.function.Supplier;

/** 发布全局会话并通过注册的工厂创建客户端实例。 */
public final class SessionLifecycle {
    private static WorldSession current;
    private static Supplier<WorldSession> factory;
    private SessionLifecycle() {}

    public static void factory(Supplier<WorldSession> replacement) {
        if (factory != null) throw new IllegalStateException("TerraVista Startup: Cannot set instance factory more than once");
        factory = replacement;
    }
    public static WorldSession current() { return current; }
    public static boolean available() { return factory != null; }
    public static void create() {
        if (factory == null) return;
        if (current != null) throw new IllegalStateException("TerraVista Startup: Cannot create multiple instances");
        try { current = factory.get(); }
        catch (WorldSession.CreationDeclined exception) {
            ModLog.info("Startup: Session creation skipped by the runtime startup setting");
        }
    }
    public static void shutdown() {
        if (current != null) {
            var previous = current;
            previous.shutdown();
            current = null;
        }
    }
}

package dev.terravista.bootstrap;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.util.thread.BlockableEventLoop;

/** 决定登录失败的升级与任务异常传播。 */
public final class StartupFailurePolicy {
    public static boolean isLogin(Packet<?> packet) {
        return packet instanceof ClientboundLoginPacket;
    }

    public static void failLogin(Exception failure) {
        throw new StartupFailure("TerraVista Startup: Force crashing due to exception during on game join", failure);
    }

    public static boolean nonRecoverable(Throwable failure) {
        if (failure instanceof StartupFailure startup) {
            if (startup.getCause() instanceof RuntimeException cause) throw cause;
            throw startup;
        }
        return BlockableEventLoop.isNonRecoverable(failure);
    }
}

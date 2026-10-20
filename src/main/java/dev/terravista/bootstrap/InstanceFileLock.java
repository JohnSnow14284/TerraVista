package dev.terravista.bootstrap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Path;

/** 持有进程级独占锁并读取启用开关。 */
public final class InstanceFileLock {
    private static FileLock held;
    private InstanceFileLock() {}

    public static boolean requested() {
        return Boolean.parseBoolean(System.getProperty("terravista.exclusiveLock"));
    }

    public static boolean acquire(Path gameDirectory) {
        var directory = gameDirectory.resolve(".terravista");
        if (!directory.toFile().isDirectory()) directory.toFile().mkdir();
        try {
            var output = new FileOutputStream(directory.resolve("terravista.lock").toFile());
            held = output.getChannel().lock(0, Long.MAX_VALUE, false);
            return true;
        } catch (NonWritableChannelException | IOException exception) {
            ModLog.error("Startup: Failed to acquire exclusive TerraVista lock file, mod will be disabled");
            return false;
        }
    }
}

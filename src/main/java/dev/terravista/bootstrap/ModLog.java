package dev.terravista.bootstrap;

import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 格式化日志并按级别输出，客户端通知交由通知服务处理。 */
public final class ModLog {
    private static final org.slf4j.Logger LOG = DiagnosticFiles.open();

    private static String caller() {
        String name = new Throwable().getStackTrace()[2].getClassName();
        return "TerraVista/" + name.substring(name.lastIndexOf('.') + 1);
    }
    private static Throwable cause(Object[] arguments) {
        Throwable result = null;
        for (Object argument : arguments) if (argument instanceof Throwable throwable) result = throwable;
        return result;
    }
    private static String text(Object value) {
        if (value == null) return "NULL";
        // 将数组转换为对象数组后格式化。
        if (value.getClass().isArray()) return Arrays.deepToString((Object[]) value);
        return value.toString();
    }
    private static String format(String prefix, Object[] arguments) {
        return prefix + Stream.of(arguments).map(ModLog::text).collect(Collectors.joining(" "));
    }
    public static void error(Object... arguments) {
        Throwable throwable = cause(arguments);
        String message = format("[" + caller() + "] ", arguments);
        LOG.error(message, throwable);
        if (RuntimeEnvironment.IN_MINECRAFT && !RuntimeEnvironment.DEDICATED_SERVER) {
            var notification = arguments.length > 0 && arguments[0] instanceof String label && label.startsWith("Import:")
                    ? Component.translatable("terravista.message.import_failed") : Component.literal(message);
            ClientNotifications.error(notification, DiagnosticFiles.currentFile());
        }
    }
    public static void warn(Object... arguments) {
        Throwable throwable = cause(arguments);
        LOG.warn(format("[" + caller() + "] ", arguments), throwable);
    }
    public static String info(Object... arguments) {
        Throwable throwable = cause(arguments);
        String message = format("[" + caller() + "] ", arguments);
        LOG.info(message, throwable);
        return message;
    }
}

package dev.terravista.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;

/** 将模组日志写入独立目录，由日志上下文关闭文件。 */
final class DiagnosticFiles {
    private static Path currentFile;
    static Path currentFile() { return currentFile; }
    static org.slf4j.Logger open() {
        var context = (LoggerContext) LogManager.getContext(false);
        var configuration = context.getConfiguration();
        var layout = PatternLayout.newBuilder().withConfiguration(configuration)
                .withPattern("[%d{HH:mm:ss.SSS}] [%t/%level] %msg%n%throwable").build();
        Appender output;
        try {
            Path game = RuntimeEnvironment.IN_MINECRAFT ? FMLPaths.GAMEDIR.get() : Path.of(System.getProperty("user.dir"));
            Path directory = Files.createDirectories(game.resolve("tv").resolve("logs"));
            output = RollingFileAppender.newBuilder().setName("TerraVistaFile").setConfiguration(configuration)
                    .setLayout(layout).withFileName(directory.resolve("terravista.log").toString())
                    .withFilePattern(directory.resolve("terravista-%i.log.gz").toString())
                    .withPolicy(CompositeTriggeringPolicy.createPolicy(OnStartupTriggeringPolicy.createPolicy(1),
                            SizeBasedTriggeringPolicy.createPolicy("20MB")))
                    .withStrategy(DefaultRolloverStrategy.newBuilder().withMax("5").withConfig(configuration).build())
                    .withImmediateFlush(true).build();
            if (output == null) throw new IllegalStateException("TerraVista diagnostic file could not be opened");
        } catch (Exception failure) {
            System.err.println("TerraVista: diagnostic file unavailable; using standard error");
            failure.printStackTrace(System.err);
            output = ConsoleAppender.newBuilder().setName("TerraVistaConsole").setConfiguration(configuration)
                    .setLayout(layout).setTarget(ConsoleAppender.Target.SYSTEM_ERR).build();
        }
        output.start();
        if (output instanceof RollingFileAppender file) currentFile = Path.of(file.getFileName()).toAbsolutePath();
        configuration.addAppender(output);
        var logger = new LoggerConfig("TerraVista", configuration.getLoggerConfig("TerraVista").getLevel(), false);
        logger.addAppender(output, null, null);
        configuration.addLogger("TerraVista", logger);
        context.updateLoggers();
        return LoggerFactory.getLogger("TerraVista");
    }

    private DiagnosticFiles() {}
}

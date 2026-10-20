package dev.terravista.storage;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** 完整复制已有存储后发布新目录，保留原数据。 */
public final class StorageDirectoryMigration {
    private StorageDirectoryMigration() {}
    public static void copyIfAbsent(Path source, Path target) {
        if (!Files.isDirectory(source) || Files.exists(target)) return;
        Path temporary = target.resolveSibling("." + target.getFileName() + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Files.createDirectories(temporary.resolve(source.relativize(directory)));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.copy(file, temporary.resolve(source.relativize(file)));
                    return FileVisitResult.CONTINUE;
                }
            });
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target); }
        } catch (IOException exception) {
            throw new IllegalStateException("TerraVista Storage: Could not copy existing world data", exception);
        } finally {
            if (Files.exists(temporary)) try {
                Files.walkFileTree(temporary, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                        if (error != null) throw error;
                        Files.delete(directory); return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException exception) { throw new IllegalStateException("TerraVista Storage: Could not release temporary storage", exception); }
        }
    }
}

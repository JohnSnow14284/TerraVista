package dev.terravista.importer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import net.minecraft.resources.Identifier;

/** 识别存档中的标准维度和自定义维度区域目录。 */
public final class SaveDirectories {
    private SaveDirectories() {}

    public static Map<Identifier, Path> find(Path save) throws IOException {
        if (!Files.isDirectory(save) || !Files.isRegularFile(save.resolve("level.dat")))
            throw new IOException("Selected folder is not a Minecraft save");
        var result = new LinkedHashMap<Identifier, Path>();
        add(result, Identifier.parse("minecraft:overworld"), save.resolve("region"));
        add(result, Identifier.parse("minecraft:the_nether"), save.resolve("DIM-1/region"));
        add(result, Identifier.parse("minecraft:the_end"), save.resolve("DIM1/region"));
        Path dimensions = save.resolve("dimensions");
        if (Files.isDirectory(dimensions)) {
            var regions = new HashSet<Path>();
            Files.walkFileTree(dimensions, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (regions.contains(directory)) return FileVisitResult.SKIP_SUBTREE;
                    Path relative = dimensions.relativize(directory);
                    Path region = directory.resolve("region");
                    if (relative.getNameCount() >= 2 && Files.isDirectory(region)) {
                        String name = relative.getName(0) + ":" + relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                        Identifier id = Identifier.tryParse(name);
                        if (id == null || result.putIfAbsent(id, region) != null) throw new IOException("Invalid or duplicate dimension: " + name);
                        regions.add(region);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if (result.isEmpty()) throw new IOException("Selected save has no dimension region directories");
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<Identifier, Path> result, Identifier id, Path directory) {
        if (Files.isDirectory(directory)) result.put(id, directory);
    }
}

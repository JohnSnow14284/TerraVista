package dev.terravista.compat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a local TV edition using the same adapter as the optional runtime bridge. */
public final class EclipsePatchExport {
    private static final Set<String> TEXT = Set.of("glsl", "vsh", "fsh", "gsh", "csh", "properties", "lang");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected original Eclipse directory and a new ZIP path");
        Path source = Path.of(args[0]).toRealPath();
        Path shaders = source.resolve("shaders");
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(source)) throw new IllegalArgumentException("Output must be outside the original Eclipse directory");
        if (!EclipsePrograms.matches(shaders)) throw new IllegalArgumentException("Unrecognized Eclipse LOD output contract");
        Files.createDirectories(output.getParent());
        int files = 0;
        try (var zip = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW));
             var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = source.relativize(path).toString().replace('\\', '/');
                if (relative.startsWith(".") || relative.endsWith("/voxy.json")) continue;
                String name = nativePath(relative);
                var entry = new ZipEntry(name);
                entry.setTime(0);
                zip.putNextEntry(entry);
                String extension = relative.substring(relative.lastIndexOf('.') + 1);
                if (relative.startsWith("shaders/") && TEXT.contains(extension)) {
                    var lines = EclipsePrograms.adapt(shaders, path, Files.readAllLines(path));
                    String text = nativePath(String.join("\n", lines));
                    // Translate option labels alongside their identifiers.
                    if (extension.equals("lang")) text = text.replace("Voxy", "TerraVista").replace("VOXY", "TERRAVISTA");
                    zip.write(text.getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(path, zip);
                }
                zip.closeEntry();
                files++;
            }
            var entry = new ZipEntry("TERRAVISTA-PATCH.md");
            entry.setTime(0);
            zip.putNextEntry(entry);
            zip.write(("Eclipse TV Vulkan edition\n\n"
                    + "Generated locally by TerraVista's exportEclipsePatch task. Original credits and license are retained.\n"
                    + "Requires TerraVista packed ABI " + VitrailPackedPrograms.ABI + " and Vitrail; select this ZIP in Vitrail.\n"
                    + "TV owns geometry/material IDs; the patch owns LOD lighting, depth consumers and water outputs.\n"
                    + "Vitrail's DISTANT family carries the dh_ entry files. No Voxy or Iris runtime is required.\n"
                    + "The legacy voxy.json files are omitted: SSBO declarations and blend/output state are explicit.\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        System.out.println("Exported Eclipse TV Vulkan patch: " + output + " (" + files + " source files)");
    }

    private static String nativePath(String text) {
        return text.replace("voxy_opaque.glsl", "tv_opaque.glsl")
                .replace("voxy_translucent.glsl", "tv_translucent.glsl")
                .replace("voxy_solid.glsl", "tv_solid.glsl");
    }

    private EclipsePatchExport() {}
}

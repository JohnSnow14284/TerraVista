package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;

/** 准备目录或压缩包中的区域输入并按索引读取。 */
final class RegionInputs {
    @FunctionalInterface
    interface RegionConsumer { void accept(NativeBuffer region, int x, int z); }
    @FunctionalInterface
    private interface Reader { void read(int index, RegionConsumer consumer) throws Exception; }

    final int count;
    private final Reader reader;

    private RegionInputs(int count, Reader reader) { this.count = count; this.reader = reader; }

    void read(int index, RegionConsumer consumer) throws Exception { reader.read(index, consumer); }

    static RegionInputs directory(File directory) {
        var files = directory.listFiles((parent, name) -> accepted(name));
        if (files == null) return null;
        Arrays.sort(files, File::compareTo);
        return new RegionInputs(files.length, (index, consumer) -> readFile(files[index], consumer));
    }

    static RegionInputs zip(File archive, String prefix) {
        try {
            prefix = prefix.replace("\\\\", "\\").replace("\\", "/");
            var zip = ZipFile.builder().setFile(archive).get();
            var entries = new ArrayList<ZipArchiveEntry>();
            for (var enumeration = zip.getEntries(); enumeration.hasMoreElements();) {
                var entry = enumeration.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                String[] path = entry.getName().split("/");
                if (accepted(path[path.length - 1])) entries.add(entry);
            }
            var selected = entries.toArray(ZipArchiveEntry[]::new);
            return new RegionInputs(selected.length, (index, consumer) -> readEntry(zip, selected[index], consumer));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean accepted(String name) {
        String[] parts = name.split("\\.");
        if (parts.length == 4 && parts[0].equals("r") && parts[3].equals("mca")) return true;
        ModLog.error("Import: Unknown file: " + name);
        return false;
    }

    private static void invalidPosition(String[] parts) {
        ModLog.error("Import: Invalid format for region position, x: \"" + parts[1] + "\" z: \"" + parts[2] + "\" skipping region");
    }

    static void readFile(File file, RegionConsumer consumer) throws IOException {
        String name = file.getName();
        String[] parts = name.split("\\.");
        if (!accepted(name)) throw new IllegalStateException();
        int x, z;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            invalidPosition(parts);
            return;
        }
        try (var channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            if (channel.size() == 0) return;
            var region = new NativeBuffer(channel.size());
            if (channel.read(region.byteBuffer(), 0) < 8192) {
                region.free();
                ModLog.warn("Import: Region file header is invalid");
                return;
            }
            consumer.accept(region, x, z);
            region.free();
        }
    }

    private static void readEntry(ZipFile zip, ZipArchiveEntry entry, RegionConsumer consumer) throws IOException {
        if (entry.getSize() == 0) return;
        var region = new NativeBuffer(entry.getSize());
        try (var channel = Channels.newChannel(zip.getInputStream(entry))) {
            if (channel.read(region.byteBuffer()) != region.bytes) {
                region.free();
                throw new IllegalStateException("TerraVista Import: Could not read full zip entry");
            }
        }
        String[] path = entry.getName().split("/");
        String[] parts = path[path.length - 1].split("\\.");
        try {
            consumer.accept(region, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            invalidPosition(parts);
        }
        region.free();
    }
}

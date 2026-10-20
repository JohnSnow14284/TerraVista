package dev.terravista.model;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.render.UploadStream;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.system.MemoryUtil;

/** 发布群系颜色快照并管理有界混合颜色表。 */
public final class BlendedTints {
    public static final int BASE = 1 << 16;
    public static final int CAPACITY = 512;
    private volatile int[][] rows = new int[0][];
    private final Int2IntOpenHashMap indices = new Int2IntOpenHashMap();
    private final int[] colours = new int[CAPACITY];
    private final ConcurrentLinkedQueue<int[]> pending = new ConcurrentLinkedQueue<>();
    private int size;

    public BlendedTints() { indices.defaultReturnValue(-1); }
    public synchronized int colour(int index) { return colours[index]; }
    public int[] row(int model) { var snapshot = rows; return model < snapshot.length ? snapshot[model] : null; }
    void row(int model, NativeBuffer data) {
        var next = Arrays.copyOf(rows, Math.max(rows.length, model + 1));
        var values = new int[(int) data.bytes / 4]; MemoryUtil.memIntBuffer(data.address, values.length).get(values);
        next[model] = values; rows = next;
    }
    void rebuild(BiomeColourUpload upload, int stride) {
        var next = new int[rows.length][];
        for (long offset = 0; offset < upload.modelIndices.bytes; offset += 8) {
            long pair = MemoryUtil.memGetLong(upload.modelIndices.address + offset);
            int model = (int) pair, base = (int) (pair >>> 32);
            if (model >= next.length) next = Arrays.copyOf(next, model + 1);
            var values = new int[stride];
            MemoryUtil.memIntBuffer(upload.colours.address + base * 4L, stride).get(values);
            next[model] = values;
        }
        rows = next;
    }
    public int index(int colour) {
        return shadedIndex(colour, 255);
    }
    public synchronized int shadedIndex(int colour, int shade) {
        colour = (colour & 0xFFFFFF) | (shade << 24);
        int key = colour & 0xFFF8F8F8;
        int found = indices.get(key);
        if (found >= 0) return found;
        int slot = size;
        if (size < CAPACITY) {
            colours[size++] = colour;
            pending.add(new int[]{slot, colours[slot]});
        } else {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                int distance = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    int delta = ((colour >>> shift) & 255) - ((colours[i] >>> shift) & 255);
                    distance += delta * delta;
                }
                if (distance < best) { best = distance; slot = i; }
            }
        }
        indices.put(key, slot);
        return slot;
    }
    boolean upload(ModelStore store) {
        boolean changed = false;
        int[] entry;
        while ((entry = pending.poll()) != null) {
            MemoryUtil.memPutInt(UploadStream.current().upload(store.colourBuffer(), (BASE + entry[0]) * 4L, 4), entry[1]);
            changed = true;
        }
        return changed;
    }
}

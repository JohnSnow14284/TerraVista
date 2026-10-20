package dev.terravista.world;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.WeakHashMap;
import net.minecraft.world.level.block.state.BlockState;

/** 复用转换线程的调色板与生物群系缓存，并以弱引用跟踪世界。 */
final class ConversionScratch {
    final int[] biomes = new int[4 * 4 * 4];
    private final WeakHashMap<StatePalette, Reference2IntOpenHashMap<BlockState>> worlds = new WeakHashMap<>();
    private int[] palette = new int[1024];
    Reference2IntOpenHashMap<BlockState> states(StatePalette world) {
        return worlds.computeIfAbsent(world, ignored -> new Reference2IntOpenHashMap<>());
    }
    int[] palette(int size) {
        if (palette.length < size) palette = new int[size];
        return palette;
    }
}

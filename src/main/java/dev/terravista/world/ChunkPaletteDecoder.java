package dev.terravista.world;

import dev.terravista.compat.LithiumPaletteBridge;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

/** 将 Minecraft 调色板槽位转换为世界持久化方块编号。 */
final class ChunkPaletteDecoder {
    private ChunkPaletteDecoder() {}
    private static int id(BlockState state, Reference2IntOpenHashMap<BlockState> cache, StatePalette world) {
        int id = -1;
        if (state != null) {
            id = cache.getOrDefault(state, -1);
            if (id == -1) { id = world.blockId(state); cache.put(state, id); }
        }
        return id;
    }
    static int fill(Palette<BlockState> source, Reference2IntOpenHashMap<BlockState> cache, StatePalette world, int[] target, boolean lithium) {
        int count = source.getSize();
        if (source instanceof LinearPalette<BlockState>) {
            for (int index = 0; index < source.getSize(); index++) target[index] = id(source.valueFor(index), cache, world);
        } else if (source instanceof HashMapPalette<BlockState>) {
            fillMissing(source, cache, world, target);
        } else if (source instanceof SingleValuePalette<BlockState>) {
            target[0] = id(source.valueFor(0), cache, world);
        } else {
            if (!(lithium && LithiumPaletteBridge.supports(source))) throw new IllegalStateException("TerraVista World: Unknown palette type: " + source);
            fillMissing(source, cache, world, target);
        }
        return count;
    }
    private static void fillMissing(Palette<BlockState> source, Reference2IntOpenHashMap<BlockState> cache, StatePalette world, int[] target) {
        for (int index = 0; index < source.getSize(); index++) {
            BlockState state = null;
            try { state = source.valueFor(index); } catch (Exception exception) { /* 接收缺失的哈希调色板槽位。 */ }
            target[index] = id(state, cache, world);
        }
    }
}

package dev.terravista.storage;

import dev.terravista.bootstrap.ModLog;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;

/** 按多数票选择完整调色板，处理空映射与平票。 */
final class PaletteConsensus {
    private record EncodedValue(byte[] bytes) {
        @Override public boolean equals(Object other) { return Arrays.equals(bytes, ((EncodedValue) other).bytes); }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }
    static Int2ObjectOpenHashMap<byte[]> select(ByteStore[] stores) {
        var votes = new Object2IntOpenHashMap<Int2ObjectOpenHashMap<EncodedValue>>();
        Int2ObjectOpenHashMap<EncodedValue> latest = null;
        for (var store : stores) {
            var palette = store.getIdMappingsData();
            if (palette.isEmpty()) continue;
            var encoded = new Int2ObjectOpenHashMap<EncodedValue>(palette.size());
            for (var entry : palette.int2ObjectEntrySet()) encoded.put(entry.getIntKey(), new EncodedValue(entry.getValue()));
            votes.addTo(encoded, 1);
            latest = encoded;
        }
        if (latest == null) return new Int2ObjectOpenHashMap<>();
        if (votes.size() != 1) {
            ModLog.error("Storage: Error id mapping not matching across all fragments, attempting to recover");
            Object2IntMap.Entry<Int2ObjectOpenHashMap<EncodedValue>> winner = null;
            for (var entry : votes.object2IntEntrySet()) {
                if (winner == null || winner.getIntValue() < entry.getIntValue()) winner = entry;
            }
            latest = winner.getKey();
        }
        var result = new Int2ObjectOpenHashMap<byte[]>(latest.size());
        for (var entry : latest.int2ObjectEntrySet()) result.put(entry.getIntKey(), entry.getValue().bytes);
        return result;
    }
}

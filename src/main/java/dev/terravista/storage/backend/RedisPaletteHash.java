package dev.terravista.storage.backend;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;

/** 读取调色板并按接口要求调整调用方缓冲与返回映射。 */
final class RedisPaletteHash {
    private final RedisConnections connections;
    RedisPaletteHash(RedisConnections connections) { this.connections = connections; }
    void put(int id, ByteBuffer data) {
        connections.request(connection -> {
            var bytes = new byte[data.remaining()];
            data.get(bytes); data.rewind();
            return connection.hset(connections.palette, RedisHashKeys.palette(id), bytes);
        });
    }
    Int2ObjectOpenHashMap<byte[]> snapshot() {
        return connections.request(connection -> {
            var mappings = connection.hgetAll(connections.palette);
            var result = new Int2ObjectOpenHashMap<byte[]>();
            if (mappings == null) return result;
            for (var entry : mappings.entrySet()) result.put(RedisHashKeys.palette(entry.getKey()), entry.getValue());
            return result;
        });
    }
}

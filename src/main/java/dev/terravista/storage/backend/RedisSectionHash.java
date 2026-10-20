package dev.terravista.storage.backend;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.NativeMemory;

/** 通过字节数组副本在原生区块缓冲与 Redis 之间传输。 */
final class RedisSectionHash {
    private final RedisConnections connections;
    RedisSectionHash(RedisConnections connections) { this.connections = connections; }
    NativeBuffer read(long key, NativeBuffer scratch) {
        return connections.request(connection -> {
            var bytes = connection.hget(connections.sections, RedisHashKeys.section(key));
            if (bytes == null) return null;
            NativeMemory.copy(bytes, scratch.address);
            return scratch.resizeView(bytes.length);
        });
    }
    void put(long key, NativeBuffer data) {
        connections.request(connection -> {
            var bytes = new byte[(int) data.bytes];
            NativeMemory.copy(data.address, bytes);
            return connection.hset(connections.sections, RedisHashKeys.section(key), bytes);
        });
    }
    void delete(long key) { connections.request(connection -> connection.hdel(connections.sections, RedisHashKeys.section(key))); }
}

package dev.terravista.storage.backend;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/** 为单次请求借用连接并在访问哈希表前认证。 */
final class RedisConnections {
    private final JedisPool pool;
    private final String user;
    private final String password;
    final byte[] sections;
    final byte[] palette;

    RedisConnections(String host, int port, String prefix, String user, String password) {
        pool = new JedisPool(host, port);
        this.user = user; this.password = password;
        sections = (prefix + "world_sections").getBytes(StandardCharsets.UTF_8);
        palette = (prefix + "id_mappings").getBytes(StandardCharsets.UTF_8);
    }
    <T> T request(Function<Jedis, T> action) {
        try (var connection = pool.getResource()) {
            if (user != null) connection.auth(user, password);
            return action.apply(connection);
        }
    }
    void close() { pool.close(); }
}

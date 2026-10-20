package dev.terravista.storage.backend;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.util.lmdb.MDBVal;
import static org.lwjgl.util.lmdb.LMDB.*;

/** 读写四字节调色板编号并从映射数据库游标复制数据。 */
final class LmdbPaletteTable {
    private final LmdbEnvironment.Database database;
    LmdbPaletteTable(LmdbEnvironment environment) { database = environment.database("id_mapping"); }
    void put(int id, ByteBuffer data) {
        database.transaction(transaction -> {
            transaction.put(transaction.stack.malloc(4).putInt(0, id), data, 0);
            return null;
        });
    }
    Int2ObjectOpenHashMap<byte[]> snapshot() {
        var result = new Int2ObjectOpenHashMap<byte[]>();
        database.transaction(MDB_RDONLY, transaction -> {
            try (var cursor = transaction.cursor()) {
                var key = MDBVal.malloc(transaction.stack);
                var value = MDBVal.malloc(transaction.stack);
                while (cursor.read(MDB_NEXT, key, value) != MDB_NOTFOUND) {
                    int id = key.mv_data().getInt(0);
                    var data = new byte[(int) value.mv_size()];
                    Objects.requireNonNull(value.mv_data()).get(data);
                    if (result.put(id, data) != null) throw new IllegalStateException("TerraVista Storage: Multiple mappings to same id");
                }
            }
            return null;
        });
        return result;
    }
    void close() { database.close(); }
}

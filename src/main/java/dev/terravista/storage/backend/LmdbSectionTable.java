package dev.terravista.storage.backend;

import dev.terravista.memory.NativeBuffer;
import dev.terravista.memory.NativeMemory;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.util.lmdb.LMDB.MDB_RDONLY;

/** 在表事务中读写八字节区块键并转移暂存数据。 */
final class LmdbSectionTable {
    private final LmdbEnvironment.Database database;
    LmdbSectionTable(LmdbEnvironment environment) { database = environment.database("world_sections"); }
    NativeBuffer read(long key, NativeBuffer scratch) {
        return database.transaction(MDB_RDONLY, transaction -> {
            var address = transaction.stack.malloc(8).putLong(0, key);
            var bytes = transaction.get(address);
            if (bytes == null) return null;
            NativeMemory.copy(MemoryUtil.memAddress(bytes), scratch.address, bytes.remaining());
            return scratch.resizeView(bytes.remaining());
        });
    }
    void put(long key, NativeBuffer data) {
        database.transaction(transaction -> {
            var address = transaction.stack.malloc(8).putLong(0, key);
            transaction.put(address, MemoryUtil.memByteBuffer(data.address, (int) data.bytes), 0);
            return null;
        });
    }
    void delete(long key) {
        database.transaction(transaction -> {
            transaction.delete(transaction.stack.malloc(8).putLong(0, key));
            return null;
        });
    }
    void close() { database.close(); }
}

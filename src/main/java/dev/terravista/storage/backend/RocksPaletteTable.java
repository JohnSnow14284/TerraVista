package dev.terravista.storage.backend;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import org.rocksdb.RocksDBException;
import org.rocksdb.ReadOptions;

/** 写入调色板后复位调用方缓冲游标。 */
final class RocksPaletteTable {
    private final RocksDatabase owner;
    RocksPaletteTable(RocksDatabase owner) { this.owner = owner; }
    void put(int id, ByteBuffer data) {
        try {
            var bytes = new byte[data.remaining()];
            data.get(bytes); data.rewind();
            owner.database.put(owner.palette, RocksKeys.palette(id), bytes);
        } catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
    Int2ObjectOpenHashMap<byte[]> snapshot() {
        var result = new Int2ObjectOpenHashMap<byte[]>();
        try (var options = new ReadOptions().setFillCache(false); var iterator = owner.database.newIterator(owner.palette, options)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) result.put(RocksKeys.palette(iterator.key()), iterator.value());
            iterator.status();
        } catch (RocksDBException exception) { throw new RuntimeException(exception); }
        return result;
    }
}

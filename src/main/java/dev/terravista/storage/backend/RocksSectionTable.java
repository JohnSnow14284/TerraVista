package dev.terravista.storage.backend;

import dev.terravista.memory.NativeBuffer;
import java.util.function.LongConsumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.ReadOptions;

/** 按区块位置格式读写与遍历层级。 */
final class RocksSectionTable {
    private final RocksDatabase owner;
    RocksSectionTable(RocksDatabase owner) { this.owner = owner; }
    NativeBuffer read(long key, NativeBuffer scratch) {
        try (var stack = MemoryStack.stackPush()) {
            var bytes = stack.malloc(8);
            RocksKeys.section(MemoryUtil.memAddress(bytes), key);
            int length = owner.database.get(owner.sections, owner.sectionRead, bytes, MemoryUtil.memByteBuffer(scratch.address, (int) scratch.bytes));
            if (length == RocksDB.NOT_FOUND) return null;
            return scratch.resizeView(length);
        } catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
    void put(long key, NativeBuffer data) {
        try (var stack = MemoryStack.stackPush()) {
            var bytes = stack.calloc(8);
            RocksKeys.section(MemoryUtil.memAddress(bytes), key);
            owner.database.put(owner.sections, owner.sectionWrite, bytes, data.byteBuffer());
        } catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
    void delete(long key) {
        try { owner.database.delete(owner.sections, RocksKeys.sectionBytes(key)); }
        catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
    void visit(int level, LongConsumer consumer) {
        try (var stack = MemoryStack.stackPush(); var options = new ReadOptions().setFillCache(false);
             var iterator = owner.database.newIterator(owner.sections, options)) {
            var bytes = stack.calloc(8);
            long address = MemoryUtil.memAddress(bytes);
            if (level != -1) {
                var start = stack.calloc(8);
                MemoryUtil.memPutLong(MemoryUtil.memAddress(start), Long.reverseBytes(Integer.toUnsignedLong(level) << 60));
                iterator.seek(start);
            } else iterator.seekToFirst();
            while (iterator.isValid()) {
                bytes.clear(); iterator.key(bytes);
                long key = RocksKeys.section(address);
                if (level != -1 && (int) ((key >> 60) & 0xf) != level) break;
                consumer.accept(key); iterator.next();
            }
            iterator.status();
        } catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
}

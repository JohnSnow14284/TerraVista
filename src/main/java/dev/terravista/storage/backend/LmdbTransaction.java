package dev.terravista.storage.backend;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.lmdb.MDBVal;
import static dev.terravista.storage.backend.LmdbEnvironment.check;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.lmdb.LMDB.*;

/** 在所属环境事务内绑定数据库并操作原生键值。 */
public final class LmdbTransaction {
    public final MemoryStack stack;
    private final long address;
    private int database;
    public LmdbTransaction(long address, MemoryStack stack) { this.address = address; this.stack = stack; }
    public LmdbTransaction bind(LmdbEnvironment.Database target) { database = target.id(); return this; }
    public LmdbTransaction put(ByteBuffer key, ByteBuffer value, int flags) {
        try (var temporary = stackPush()) {
            check(mdb_put(address, database, MDBVal.malloc(temporary).mv_data(key), MDBVal.malloc(temporary).mv_data(value), flags));
            return this;
        }
    }
    public LmdbTransaction delete(ByteBuffer key) {
        try (var temporary = stackPush()) {
            check(mdb_del(address, database, MDBVal.malloc(temporary).mv_data(key), null));
            return this;
        }
    }
    public ByteBuffer get(ByteBuffer key) {
        try (var temporary = stackPush()) {
            var value = MDBVal.malloc(temporary);
            int status = mdb_get(address, database, MDBVal.calloc(temporary).mv_data(key), value);
            if (status == MDB_NOTFOUND) return null;
            check(status);
            return value.mv_data();
        }
    }
    public LmdbCursor cursor() {
        try (var temporary = stackPush()) {
            var output = temporary.mallocPointer(1);
            check(mdb_cursor_open(address, database, output));
            return new LmdbCursor(output.get(0));
        }
    }
}

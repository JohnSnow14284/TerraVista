package dev.terravista.storage.backend;

import org.lwjgl.util.lmdb.MDBVal;
import static dev.terravista.storage.backend.LmdbEnvironment.check;
import static org.lwjgl.util.lmdb.LMDB.*;

/** 将原生游标的生命周期限制在所属事务内。 */
public final class LmdbCursor implements AutoCloseable {
    private final long address;
    public LmdbCursor(long address) { this.address = address; }
    public int read(int operation, MDBVal key, MDBVal value) {
        int status = mdb_cursor_get(address, key, value, operation);
        if (status != MDB_SUCCESS && status != MDB_NOTFOUND) check(status);
        return status;
    }
    @Override public void close() { mdb_cursor_close(address); }
}

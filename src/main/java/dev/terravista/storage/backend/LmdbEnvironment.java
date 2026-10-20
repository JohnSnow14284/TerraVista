package dev.terravista.storage.backend;

import java.util.function.Function;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.lmdb.MDBEnvInfo;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.lmdb.LMDB.*;

/** 管理原生环境所有权与事务提交、中止边界。 */
public final class LmdbEnvironment {
    private final long address;
    private LmdbEnvironment(long address) { this.address = address; }

    public static final class Builder {
        private final long address;
        public Builder() {
            try (var stack = stackPush()) {
                var output = stack.mallocPointer(1);
                check(mdb_env_create(output));
                address = output.get(0);
            }
        }
        public Builder maxDatabases(int count) { check(mdb_env_set_maxdbs(address, count)); return this; }
        public Builder open(String directory, int flags) { check(mdb_env_open(address, directory, flags, 0664)); return this; }
        public LmdbEnvironment build() { return new LmdbEnvironment(address); }
    }

    @FunctionalInterface public interface TransactionAction<T> { T execute(MemoryStack stack, long transaction); }
    public static void check(int code) {
        if (code != MDB_SUCCESS) throw new IllegalStateException("Code: " + code + " TerraVista LMDB: " + mdb_strerror(code));
    }
    public void close() { mdb_env_close(address); }
    public void mapSize(long bytes) { check(mdb_env_set_mapsize(address, bytes)); }
    public long mapSize() {
        try (var stack = stackPush()) {
            var info = MDBEnvInfo.calloc(stack);
            check(mdb_env_info(address, info));
            return info.me_mapsize();
        }
    }
    public LmdbEnvironment flush(boolean force) { check(mdb_env_sync(address, force)); return this; }
    public <T> T transaction(TransactionAction<T> action) { return transaction(0, action); }
    public <T> T transaction(int flags, TransactionAction<T> action) { return transaction(0, flags, action); }
    public <T> T transaction(long parent, int flags, TransactionAction<T> action) {
        T result;
        try (var stack = stackPush()) {
            var output = stack.mallocPointer(1);
            check(mdb_txn_begin(address, parent, flags, output));
            long transaction = output.get(0);
            int status;
            try { result = action.execute(stack, transaction); status = mdb_txn_commit(transaction); }
            catch (Throwable failure) { mdb_txn_abort(transaction); throw failure; }
            // 离开中止作用域后检查提交返回的错误码。
            check(status);
        }
        return result;
    }
    public Database database(String name) { return database(name, MDB_CREATE | MDB_INTEGERKEY); }
    public Database database(String name, int flags) { return new Database(name, flags); }

    public final class Database {
        private final int id;
        public Database(String name, int flags) {
            id = LmdbEnvironment.this.transaction((stack, transaction) -> {
                var output = stack.mallocInt(1);
                check(mdb_dbi_open(transaction, name, flags, output));
                return output.get(0);
            });
        }
        public int id() { return id; }
        public void close() { mdb_dbi_close(address, id); }
        public <T> T transaction(Function<LmdbTransaction, T> action) { return transaction(0, action); }
        public <T> T transaction(int flags, Function<LmdbTransaction, T> action) {
            return LmdbEnvironment.this.transaction(flags, (stack, transaction) -> action.apply(new LmdbTransaction(transaction, stack).bind(this)));
        }
    }
}

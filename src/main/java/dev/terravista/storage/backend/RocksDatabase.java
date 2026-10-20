package dev.terravista.storage.backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.rocksdb.*;

/** 按数据库生命周期管理列族、原生选项与释放顺序。 */
final class RocksDatabase {
    private static HyperClockCache sharedCache;
    private static int cacheUsers;
    final RocksDB database;
    final ColumnFamilyHandle sections;
    final ColumnFamilyHandle palette;
    final ReadOptions sectionRead;
    final WriteOptions sectionWrite;
    private final List<AbstractImmutableNativeReference> retirement = new ArrayList<>();
    private final List<ColumnFamilyHandle> handles = new ArrayList<>();
    private boolean closed;
    private boolean cacheHeld;

    private static synchronized HyperClockCache acquireCache() {
        if (sharedCache == null) sharedCache = new HyperClockCache(128 * 1024L * 1024L, 0, 4, false);
        cacheUsers++;
        return sharedCache;
    }
    private static synchronized void releaseCache() {
        if (--cacheUsers == 0) {
            sharedCache.close();
            sharedCache = null;
        }
    }

    RocksDatabase(String path) {
        RocksDB.loadLibrary();
        var common = new ColumnFamilyOptions().setCompressionType(CompressionType.ZSTD_COMPRESSION).optimizeForSmallDb();
        var sectionOptions = new ColumnFamilyOptions().setCompressionType(CompressionType.NO_COMPRESSION)
                .setCompactionPriority(CompactionPriority.MinOverlappingRatio).setLevelCompactionDynamicLevelBytes(true).optimizeForPointLookup(128);
        var cache = acquireCache();
        cacheHeld = true;
        var filter = new BloomFilter(10);
        sectionOptions.setTableFormatConfig(new BlockBasedTableConfig()
                .setCacheIndexAndFilterBlocksWithHighPriority(true).setBlockCache(cache).setDataBlockHashTableUtilRatio(0.75)
                .setDataBlockIndexType(DataBlockIndexType.kDataBlockBinaryAndHash).setFilterPolicy(filter));
        var descriptors = Arrays.asList(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, common),
                new ColumnFamilyDescriptor("world_sections".getBytes(), sectionOptions),
                new ColumnFamilyDescriptor("id_mappings".getBytes(), common));
        var options = new DBOptions().setAvoidUnnecessaryBlockingIO(true).setIncreaseParallelism(2)
                .setCreateIfMissing(true).setCreateMissingColumnFamilies(true).setMaxTotalWalSize(1024 * 1024 * 128);
        retirement.addAll(List.of(filter, common, sectionOptions, options));
        RocksDB opened = null;
        try {
            database = opened = RocksDB.open(options, path, descriptors, handles);
            sectionRead = new ReadOptions();
            retirement.add(sectionRead);
            sectionWrite = new WriteOptions();
            retirement.add(sectionWrite);
            sections = handles.get(1); palette = handles.get(2);
            database.flushWal(true);
        } catch (RocksDBException | RuntimeException exception) {
            try { retire(opened); }
            catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw new RuntimeException(exception);
        }
    }
    void flush() {
        try { database.flushWal(true); }
        catch (RocksDBException exception) { throw new RuntimeException(exception); }
    }
    synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try { flush(); }
        catch (RuntimeException exception) { failure = exception; }
        try { retire(database); }
        catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
    private void retire(RocksDB opened) {
        RuntimeException failure = null;
        for (var handle : handles) {
            try { handle.close(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        try { if (opened != null) opened.closeE(); }
        catch (RocksDBException | RuntimeException exception) {
            if (failure == null) failure = new RuntimeException(exception);
            else failure.addSuppressed(exception);
        }
        for (int index = retirement.size() - 1; index >= 0; index--) {
            try { retirement.get(index).close(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (cacheHeld) {
            cacheHeld = false;
            try { releaseCache(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }
}

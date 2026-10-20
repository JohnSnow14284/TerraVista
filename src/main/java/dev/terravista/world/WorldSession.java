package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.importer.ImportCoordinator;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.storage.SectionStore;
import dev.terravista.task.ServiceWorkers;
import dev.terravista.task.WeightedScheduler;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 管理世界会话服务及其生命周期。 */
public final class WorldSession {
    public static final class CreationDeclined extends RuntimeException {}

    private final ServiceWorkers workers;
    private final SectionSaveQueue saves;
    private final ChunkIngestQueue ingest;
    private final ImportCoordinator imports;
    private final WorldRegistry worlds;
    private final Thread cleaner;
    private final Predicate<WorldIdentity> ingestPolicy;
    public final BooleanSupplier savingAllowed;

    public WorldSession(BooleanSupplier enabled, Function<WorldIdentity, SectionStore> storage,
                        Supplier<ImportCoordinator> createImports) {
        // 默认接收所有世界身份。
        this(enabled, storage, createImports, identity -> true);
    }
    public WorldSession(BooleanSupplier enabled, Function<WorldIdentity, SectionStore> storage,
                        Supplier<ImportCoordinator> createImports, Predicate<WorldIdentity> ingestPolicy) {
        if (!enabled.getAsBoolean()) throw new CreationDeclined();
        ModLog.info("World: Initializing TerraVista instance");
        workers = new ServiceWorkers();
        saves = new SectionSaveQueue(scheduler());
        ingest = new ChunkIngestQueue(scheduler());
        imports = createImports.get();
        worlds = new WorldRegistry(storage, saves, this);
        this.ingestPolicy = ingestPolicy;
        savingAllowed = () -> saves.pending() < 1200;
        cleaner = new Thread(this::cleanWorlds);
        cleaner.setPriority(Thread.MIN_PRIORITY);
        cleaner.setName("TerraVista world cleanup");
        cleaner.setDaemon(true);
        cleaner.start();
    }
    private void cleanWorlds() {
        try {
            while (worlds.running()) {
                Thread.sleep(1000);
                worlds.cleanIdle();
            }
        } catch (InterruptedException exception) {
            // 中断结束清理线程。
        } catch (Exception exception) { ModLog.error("World: Exception in world cleaner", exception); }
    }
    public WorldRegistry worlds() { return worlds; }
    public ServiceWorkers workers() { return workers; }
    public WeightedScheduler scheduler() { return workers.scheduler; }
    public ChunkIngestQueue ingest() { return ingest; }
    public ImportCoordinator imports() { return imports; }
    public boolean running() { return worlds.running(); }
    public boolean ingestEnabled(WorldIdentity identity) { return ingestPolicy.test(identity); }
    public void dedicatedThreads(int count) {
        if (count < 0) throw new IllegalArgumentException("TerraVista World: Num threads <0");
        if (workers.resize(count)) ModLog.info("World: Dedicated TerraVista thread pool size: " + count);
    }
    public void updateDedicatedThreads() { dedicatedThreads(3); }
    public void debug(List<String> lines) {
        lines.add("TerraVista native buffers count/MB: " + NativeBuffer.count() + "/" + (NativeBuffer.totalBytes() / 1_000_000));
        lines.add("TerraVista ingest/save/[active sections]: " + ingest.pending() + "/" + saves.pending() + "/[" + worlds.activeSectionCounts() + "]");
    }
    public void shutdown() {
        ModLog.info("World: Shutting down TerraVista instance");
        worlds.stopAdmission();
        try { cleaner.join(); }
        catch (InterruptedException exception) { throw new RuntimeException(exception); }
        worlds.cleanIdle();
        worlds.cancelImports(world -> imports.cancel(world));
        try { ingest.shutdown(); } catch (Exception exception) { ModLog.error(exception); }
        worlds.finishShutdown(() -> {
            saves.shutdown();
            workers.shutdown();
        }, saves::checkFailure);
        SectionArrayPool.clear();
    }
}

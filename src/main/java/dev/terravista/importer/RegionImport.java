package dev.terravista.importer;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.memory.NativeBuffer;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.task.WorkService;
import dev.terravista.task.WorkerBinding;
import dev.terravista.world.VoxelWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.io.File;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** 调度区域读取和区块转换，发布导入进度并管理世界引用。 */
public final class RegionImport implements ImportJob {
    private final VoxelWorld world;
    private final AtomicInteger estimated = new AtomicInteger();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger processed = new AtomicInteger();
    private final ConcurrentLinkedDeque<Runnable> pending = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final WorkService service;
    private final ChunkNbtDecoder decoder;
    private volatile boolean active;
    private volatile Thread reader;
    private Progress progress;
    private IntConsumer completed;

    public RegionImport(VoxelWorld world, Level level, WeightedScheduler scheduler, BooleanSupplier limiter) {
        this.world = world;
        service = scheduler.register(() -> WorkerBinding.unmanaged(() -> pending.poll().run()), 3, "TerraVista region reader", limiter);
        decoder = new ChunkNbtDecoder(world, level);
    }

    public void directory(File directory) {
        var inputs = RegionInputs.directory(directory);
        if (inputs != null) prepare(inputs);
    }

    public void zip(File archive, String prefix) {
        var inputs = RegionInputs.zip(archive, prefix);
        try { prepare(inputs); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override public void start(Progress progress, IntConsumer completed) {
        if (active) throw new IllegalStateException();
        if (reader == null) {
            service.shutdown();
            completed.accept(0);
            return;
        }
        active = true;
        world.acquireRef();
        this.progress = progress;
        this.completed = completed;
        reader.start();
    }

    @Override public VoxelWorld world() { return world; }
    @Override public boolean successful() { return !failed.get() && !active; }
    public boolean busy() { return active || reader != null; }
    @Override public boolean running() { return active || (reader != null && reader.isAlive()); }

    private void prepare(RegionInputs inputs) {
        accepted.set(0);
        estimated.set(0);
        processed.set(0);
        reader = new Thread(() -> readRegions(inputs));
        reader.setName("TerraVista region reader");
    }

    private void readRegions(RegionInputs inputs) {
        try {
            estimated.addAndGet(inputs.count * 1024);
            for (int index = 0; index < inputs.count; index++) {
                estimated.addAndGet(-1024);
                try { inputs.read(index, this::enqueueRegion); }
                catch (Exception e) { throw new RuntimeException(e); }
                while (accepted.get() - processed.get() > 10_000 && active) sleep(1);
                if (!active) {
                    failed.set(true);
                    return;
                }
            }
            service.awaitEmpty();
            while (processed.get() != accepted.get() && active) {
                Thread.yield();
                sleep(10);
            }
        } catch (Exception exception) {
            failed.set(true);
            ModLog.error("Import: Region reading failed", exception);
        } finally {
            service.awaitEmpty();
            active = false;
            if (!stopped.getAndSet(true)) {
                service.shutdown();
                world.releaseRef();
            }
            completed.accept(accepted.get());
            reader = null;
        }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private void enqueueRegion(NativeBuffer region, int x, int z) {
        RegionSectors.scan(region, (format, payload) -> {
            pending.add(() -> convert(format, payload, x, z));
            accepted.incrementAndGet();
            estimated.incrementAndGet();
            service.submit();
        });
    }

    private void convert(byte format, NativeBuffer payload, int x, int z) {
        if (!active) {
            payload.free();
            return;
        }
        try {
            try (var stream = RegionChunkStreams.decode(format, payload)) {
                if (stream == null) {
                    failed.set(true);
                    ModLog.error("Import: Chunk decompression did not produce an input stream");
                }
                else chunk(NbtIo.read(stream), x, z);
            }
        } catch (Exception e) {
            failed.set(true);
            ModLog.error("Import: Chunk could not be decoded", e);
        } finally {
            payload.free();
            progress.update(processed.incrementAndGet(), estimated.get());
        }
    }

    private void chunk(CompoundTag chunk, int regionX, int regionZ) {
        if (!chunk.contains("Status")) {
            return;
        }
        var status = ChunkStatus.byName(chunk.getStringOr("Status", null));
        if (status != ChunkStatus.FULL && status != ChunkStatus.EMPTY) {
            return;
        }
        try {
            int x = chunk.getIntOr("xPos", Integer.MIN_VALUE);
            int z = chunk.getIntOr("zPos", Integer.MIN_VALUE);
            if (x >> 5 != regionX || z >> 5 != regionZ) {
                ModLog.error("Import: Chunk position is not located in correct region, expected: (" + regionX + ", " + regionZ
                        + "), got: (" + (x >> 5) + ", " + (z >> 5) + "), importing anyway");
            }
            for (var entry : chunk.getList("sections").orElseThrow()) {
                var section = (CompoundTag) entry;
                decoder.section(x, section.getIntOr("Y", Integer.MIN_VALUE), z, section);
            }
        } catch (Exception e) {
            failed.set(true);
            ModLog.error("Import: World chunk import failed:", e);
        }
    }

    @Override public void shutdown() {
        if (stopped.getAndSet(true)) return;
        failed.set(true);
        active = false;
        var currentReader = reader;
        if (currentReader != null) {
            try { currentReader.join(); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
        }
        if (service.live()) {
            world.releaseRef();
            service.shutdown();
        }
        while (!pending.isEmpty()) pending.poll().run();
    }
}

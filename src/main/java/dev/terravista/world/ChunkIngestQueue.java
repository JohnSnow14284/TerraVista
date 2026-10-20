package dev.terravista.world;

import dev.terravista.bootstrap.ModLog;
import dev.terravista.task.WeightedScheduler;
import dev.terravista.task.WorkService;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** 持有排队的区块任务及其世界引用。 */
public final class ChunkIngestQueue {
    private static final ThreadLocal<ChunkVoxels> CHUNK = ThreadLocal.withInitial(ChunkVoxels::empty);
    private record Ingest(int x, int y, int z, VoxelWorld world, LevelChunkSection section, DataLayer block, DataLayer sky) {}
    private final WorkService service;
    private final ConcurrentLinkedDeque<Ingest> queue = new ConcurrentLinkedDeque<>();

    public ChunkIngestQueue(WeightedScheduler scheduler) {
        service = scheduler.registerUnmanaged(() -> this::runNext, 5000, "TerraVista chunk capture");
    }
    private void runNext() {
        var task = queue.pop();
        try {
            var section = task.section;
            var chunk = CHUNK.get().position(task.x, task.y, task.z);
            if (section.hasOnlyAir() && task.block == null && task.sky == null) {
                WorldChunkUpdates.apply(task.world, chunk.clear());
            } else {
                var converted = ChunkVoxelConverter.convert(chunk, task.world.palette(), section.getStates(),
                        section.getBiomes(), ChunkLightSampler.from(task.block, task.sky));
                ChunkLodBuilder.build(converted, task.world.palette());
                WorldChunkUpdates.apply(task.world, converted);
            }
        } finally {
            task.world.releaseRef();
        }
    }
    public synchronized boolean enqueue(VoxelWorld world, LevelChunkSection section, int x, int y, int z, DataLayer block, DataLayer sky) {
        if (!service.live()) return false;
        world.acquireRef();
        var task = new Ingest(x, y, z, world, section, block, sky);
        queue.add(task);
        try {
            if (!service.submit()) {
                if (queue.removeLastOccurrence(task)) world.releaseRef();
                return false;
            }
            return true;
        } catch (Exception exception) {
            ModLog.error("World: Executing had an error: assume shutting down, aborting", exception);
            if (queue.removeLastOccurrence(task)) world.releaseRef();
            return false;
        }
    }
    public boolean live() { return service.live(); }
    public int pending() { return service.pending(); }
    public synchronized void shutdown() {
        if (!service.live()) return;
        service.shutdown();
        while (!queue.isEmpty()) queue.pop().world.releaseRef();
    }
}

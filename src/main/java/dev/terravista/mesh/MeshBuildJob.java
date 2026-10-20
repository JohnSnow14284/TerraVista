package dev.terravista.mesh;

import dev.terravista.model.PendingModel;
import dev.terravista.world.ResidentSection;
import dev.terravista.world.SectionCoordinates;
import dev.terravista.world.VoxelBits;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/** 管理线程网格构建、缺失模型去重与重试。 */
final class MeshBuildJob {
    private final MeshBuildQueue queue;
    private final SectionMesher mesher;
    private final IntOpenHashSet missed = new IntOpenHashSet(128);

    MeshBuildJob(MeshBuildQueue queue) {
        this.queue = queue;
        mesher = new SectionMesher(queue.world, queue.models.catalog, queue.meshlets);
    }
    private void request(int block) {
        if (!queue.models.catalog.hasModel(block))
            if (missed.add(block)) queue.models.request(block);
    }
    private void requestInner(ResidentSection section) {
        for (long state : section.contents().blocks()) {
            int block = VoxelBits.block(state);
            if (block != 0) request(block);
        }
    }
    private void requestOuter(int mask, long[] raw) {
        for (int face = 0; face < 6; face++) {
            if ((mask & (1 << face)) == 0) continue;
            for (int index = 0; index < 1024; index++) {
                int block = VoxelBits.block(raw[index + face * 1024]);
                if (block != 0) request(block);
            }
        }
    }
    void run() {
        var request = queue.pending.poll();
        queue.queued.decrementAndGet();
        boolean release = true;
        queue.removeForWork(request);
        var section = request.section == null ? queue.world.acquireExisting(request.position) : request.section;
        if (section == null) {
            if (queue.consumer != null) queue.consumer.accept(SectionMesh.empty(request.position));
            return;
        }
        SectionMesh mesh = null;
        boolean requeued = false;
        try { mesh = mesher.generate(section); }
        catch (PendingModel pending) {
            var replacement = queue.restore(request);
            if (replacement != null) {
                if (pending.blockId) request(pending.requestedId);
                if (request.requestedInner) replacement.requestedInner = true;
                if (request.requestedOuter) replacement.requestedOuter = true;
                if (request.section != null) queue.retained.decrementAndGet();
                request.section = null;
                release = true;
                request = null;
            }
            if (request != null) {
                if (pending.blockId) request(pending.requestedId);
                if (request.requestedOuter || request.requestedInner) MeshBuildQueue.FAILURE_COUNT.incrementAndGet();
                if (request.requestedInner && request.requestedOuter) request.attempts++;
                else {
                    if (request.requestedInner) request.attempts++;
                    if (!request.requestedInner) {
                        if (pending.retryData == null) requestInner(section);
                        request.requestedInner = true;
                    }
                    if (request.requestedOuter) request.attempts++;
                    if (!request.requestedOuter && pending.retryData != null) {
                        requestOuter(pending.retryMask, pending.retryData);
                        request.requestedOuter = true;
                    }
                    request.addition = SectionCoordinates.level(request.position) > 2 ? 1 : 0;
                }
                if (request.section == null) {
                    if (queue.retained.get() < MeshBuildQueue.RETAIN_LIMIT) {
                        queue.retained.incrementAndGet(); request.section = section; release = false;
                    }
                } else release = false;
                queue.requeue(request);
                requeued = true;
            }
        } finally {
            if (!requeued && request != null) queue.discard(request);
            if (release || !requeued) {
                if (request != null && request.section != null) queue.retained.decrementAndGet();
                section.release();
            }
        }
        if (mesh != null) {
            if (queue.consumer != null) queue.consumer.accept(mesh);
            else mesh.free();
        }
    }
    void free() { mesher.free(); }
}

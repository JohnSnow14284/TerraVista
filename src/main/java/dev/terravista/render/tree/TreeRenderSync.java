package dev.terravista.render.tree;

import dev.terravista.render.DeviceBuffer;
import it.unimi.dsi.fastutil.ints.IntConsumer;

/** 在渲染线程应用完整发布并将结果对象返回池中。 */
final class TreeRenderSync {
    private final TreeInbox inbox;
    private final TreePublishQueue publications;
    private final SectionGeometryStorage geometry;
    private final NodeUploadTarget uploads;
    private IntConsumer rootAdded, rootRemoved;
    int lastNode;
    long usedBytes;

    TreeRenderSync(TreeInbox inbox, TreePublishQueue publications,
                   SectionGeometryStorage geometry, NodeUploadTarget uploads) {
        this.inbox = inbox;
        this.publications = publications;
        this.geometry = geometry;
        this.uploads = uploads;
    }
    void rootCallbacks(IntConsumer added, IntConsumer removed) { rootAdded = added; rootRemoved = removed; }
    void tick(DeviceBuffer nodes, VisibilityEviction cleaner) {
        if (inbox.failure != null) throw new RuntimeException(inbox.failure);
        var result = publications.takePublished();
        if (result == null) return;
        if (!result.roots.isEmpty()) {
            var iterator = result.roots.intIterator();
            while (iterator.hasNext()) {
                int operation = iterator.nextInt();
                if ((operation & (1 << 31)) != 0) rootAdded.accept(operation & (-1 >>> 1));
                else rootRemoved.accept(operation);
            }
        }
        var store = (MeshBufferStorage) geometry;
        store.size(result.sections);
        var copy = result.geometry;
        if (!copy.destinations.isEmpty()) {
            store.ensureElement(copy.maxElement);
            int count = copy.destinations.size();
            int bytes = (int) copy.arena.extent() * 8;
            uploads.copyRanges(copy.headers.address, count, copy.data.address, bytes, store);
        }
        if (!result.scatterLocations.isEmpty())
            uploads.scatter(result.scatter.address, result.scatterLocations.size(), nodes, store);
        if (!result.cleaner.isEmpty()) cleaner.updateSlots(result.cleaner);
        lastNode = result.lastNode;
        usedBytes = result.usedBytes;
        publications.recycle(result);
    }
}

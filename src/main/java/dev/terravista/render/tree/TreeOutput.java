package dev.terravista.render.tree;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/** 合并工作线程变更并保留待上传原生内存的所有权。 */
final class TreeOutput {
    private final RenderTree tree;
    private final MeshAllocationQueue allocation;
    private final TreePublishQueue publications;
    final IntOpenHashSet roots = new IntOpenHashSet();
    final IntOpenHashSet cleaner = new IntOpenHashSet();
    boolean waitForSync;

    TreeOutput(RenderTree tree, MeshAllocationQueue allocation, TreePublishQueue publications) {
        this.tree = tree;
        this.allocation = allocation;
        this.publications = publications;
    }
    void rootAdded(int id) { rootChanged(id, true); }
    void rootRemoved(int id) { rootChanged(id, false); }
    private void rootChanged(int id, boolean added) {
        int operation = added ? id | (1 << 31) : id;
        int opposite = added ? id : id | (1 << 31);
        if (!roots.remove(opposite))
            if (!roots.add(operation)) throw new IllegalStateException();
    }
    void allocated(int id) {
        cleaner.remove(id);
        cleaner.add(id | (1 << 31));
    }
    void freed(int id) {
        cleaner.remove(id | (1 << 31));
        cleaner.add(id);
    }
    void publish(TreeInbox inbox) {
        if (waitForSync) {
            while (publications.hasPublished() && inbox.running) {
                try { Thread.sleep(10); }
                catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
            }
        }
        var result = publications.takePublished();
        if (result == null) {
            waitForSync = false;
            result = publications.acquire();
            result.roots.addAll(roots);
            roots.clear();
            upload(result);
            // 新结果直接接收首次上传分配。
            allocation.removals().clear();
            result.cleaner.addAll(cleaner);
            cleaner.clear();
        } else {
            mergeRoots(result);
            mergeCleaner(result);
            var removed = allocation.removals();
            if (!removed.isEmpty()) {
                var iterator = removed.intIterator();
                while (iterator.hasNext()) result.geometry.remove(iterator.nextInt());
                removed.clear();
            }
            upload(result);
        }
        scatter(result);
        result.sections = allocation.size();
        result.usedBytes = allocation.usedBytes();
        result.lastNode = tree.lastNode();
        waitForSync |= result.geometry.elements * 8L > (2L << 20);
        waitForSync |= result.cleaner.size() > 1024;
        waitForSync |= result.scatterLocations.size() > 4096;
        waitForSync |= result.roots.size() > 10;
        publications.publish(result);
    }
    private void mergeRoots(TreeDelta result) {
        if (roots.isEmpty()) return;
        var iterator = roots.intIterator();
        while (iterator.hasNext()) {
            int operation = iterator.nextInt();
            if (!result.roots.remove(operation ^ (1 << 31))) result.roots.add(operation);
        }
        roots.clear();
    }
    private void mergeCleaner(TreeDelta result) {
        if (cleaner.isEmpty()) return;
        var iterator = cleaner.intIterator();
        while (iterator.hasNext()) {
            int operation = iterator.nextInt();
            result.cleaner.remove(operation ^ (1 << 31));
            result.cleaner.add(operation);
        }
        cleaner.clear();
    }
    private void upload(TreeDelta result) {
        var uploads = allocation.uploads();
        if (uploads.isEmpty()) return;
        var iterator = uploads.int2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            result.geometry.upload(entry.getIntKey(), entry.getValue());
            entry.getValue().free();
        }
        uploads.clear();
    }
    private void scatter(TreeDelta result) {
        var geometryIds = allocation.changedIds();
        if (!geometryIds.isEmpty()) {
            var iterator = geometryIds.intIterator();
            while (iterator.hasNext()) {
                int id = iterator.nextInt();
                int location = (id << 1) | (1 << 31);
                long first = result.scatterAddress(location, 1);
                long second = result.scatterAddress(location + 1, 0);
                allocation.writeMetadataSplit(id, first, second);
            }
            geometryIds.clear();
        }
        var nodeIds = tree.dirtyNodes();
        if (!nodeIds.isEmpty()) {
            var iterator = nodeIds.intIterator();
            while (iterator.hasNext()) {
                int id = iterator.nextInt();
                long address = result.scatterAddress(id);
                tree.writeNode(id, address);
            }
            nodeIds.clear();
        }
    }
}

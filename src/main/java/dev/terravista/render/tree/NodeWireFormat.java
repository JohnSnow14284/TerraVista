package dev.terravista.render.tree;

import org.lwjgl.system.MemoryUtil;

/** 定义含空值与空节点哨兵的十六字节 GPU 节点格式。 */
final class NodeWireFormat {
    private NodeWireFormat() {}

    static void write(NodeRecords nodes, long address, int node) {
        if (!nodes.contains(node)) {
            MemoryUtil.memPutLong(address, -1);
            MemoryUtil.memPutLong(address + 8, -1);
            return;
        }
        long position = nodes.position(node);
        MemoryUtil.memPutInt(address, (int) (position >> 32)); address += 4;
        MemoryUtil.memPutInt(address, (int) position); address += 4;
        int geometryWord = 0, childWord = 0;
        short flags = 0;
        flags |= (short) (nodes.requestInFlight(node) ? 1 : 0);
        flags |= (short) ((nodes.childCount(node) - 1) << 2);
        boolean eligibleForCleaning = false;
        eligibleForCleaning |= nodes.childrenAreLeaves(node);
        flags |= (short) (eligibleForCleaning ? 1 << 5 : 0);
        int geometry = nodes.geometry(node);
        if (geometry == -2) geometryWord |= 0xFFFFFF - 1;
        else if (geometry == -1) geometryWord |= 0xFFFFFF;
        else geometryWord |= geometry & 0xFFFFFF;
        int child = nodes.childBase(node);
        childWord |= child & 0xFFFFFF;
        geometryWord |= (flags & 0xFF) << 24;
        childWord |= ((flags >> 8) & 0xFF) << 24;
        MemoryUtil.memPutInt(address, geometryWord); address += 4;
        MemoryUtil.memPutInt(address, childWord); address += 4;
    }
}

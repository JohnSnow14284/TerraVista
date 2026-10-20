#include "format/position.glsl"
layout(binding = NODE_DATA_BINDING, std430) restrict buffer NodeData {
// 通过可读写节点缓冲记录状态标记。

// 每个节点占十六字节。
    uvec4[] nodes;
};

// 前两个字共同编码节点位置。

// 集中访问并解码节点数据。

struct UnpackedNode {
    uint nodeId;

    uvec2 rawPos;

    ivec3 pos;
    uint lodLevel;

    uint flags;

    uint meshPtr;
    uint childPtr;
};

#define NULL_NODE ((1<<24)-1)
#define EMPTY_QUEUE_ID ((1<<24)-2)
#define NULL_MESH ((1<<24)-1)
#define EMPTY_MESH ((1<<24)-2)

uvec4 unpackNode(out UnpackedNode node, uint nodeId) {
    uvec4 compactedNode = nodes[nodeId];
    node.nodeId = nodeId;
    node.lodLevel = getLoDLevel(compactedNode.xy);
    node.rawPos = compactedNode.xy;
    node.pos = getLoDPosition(compactedNode.xy);

    node.meshPtr = compactedNode.z&0xFFFFFFu;
    node.childPtr = compactedNode.w&0xFFFFFFu;
    node.flags = ((compactedNode.z>>24)&0xFFu) | (((compactedNode.w>>24)&0xFFu)<<8);
    return compactedNode;
}

bool hasMesh(in UnpackedNode node) {
    return node.meshPtr != NULL_MESH;
}

bool isEmptyMesh(in UnpackedNode node) {
    return node.meshPtr == EMPTY_MESH;// 处理特殊标记。
}

bool hasChildren(in UnpackedNode node) {
    return node.childPtr != NULL_NODE;
}

bool childListIsEmpty(in UnpackedNode node) {
    return node.childPtr == EMPTY_QUEUE_ID;
}

bool hasRequested(in UnpackedNode node) {
    return (node.flags&1u) != 0u;
}

uint getMesh(in UnpackedNode node) {
    return node.meshPtr;
}

uint getId(in UnpackedNode node) {
    return node.nodeId;
}

uint getChildCount(in UnpackedNode node) {
    return ((node.flags >> 2)&7U)+1;
}

uint getChildPtr(in UnpackedNode node) {
    return node.childPtr;
}

uvec2 getRawPos(in UnpackedNode node) {
    return node.rawPos;
}

void markRequested(inout UnpackedNode node) {
    node.flags |= 1u;
    nodes[node.nodeId].z |= 1u<<24;
}

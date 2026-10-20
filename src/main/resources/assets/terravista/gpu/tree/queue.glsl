#define SENTINAL_OUT_OF_BOUNDS uint(-1)

layout(push_constant) uniform PushConstants { uint queueIdx; };

layout(binding = NODE_QUEUE_META_BINDING, std430) restrict buffer NodeQueueMeta {
    uvec4 nodeQueueMetadata[MAX_ITERATIONS];
};

layout(binding = NODE_QUEUE_SOURCE_BINDING, std430) restrict readonly buffer NodeQueueSource {
    uint[] nodeQueueSource;
};

layout(binding = NODE_QUEUE_SINK_BINDING, std430) restrict writeonly buffer NodeQueueSink {
    uint[] nodeQueueSink;
};

uint getCurrentNode() {
    if (nodeQueueMetadata[queueIdx].w <= gl_GlobalInvocationID.x) {
        return SENTINAL_OUT_OF_BOUNDS;
    }
    return nodeQueueSource[gl_GlobalInvocationID.x];
}

uint nodePushIndex = -1;
void pushNodesInit(uint nodeCount) {

    #ifdef SUBGROUP_QUEUE
    // 仅对当前活动调用执行子组操作。
    // 允许遍历分支与尾部形成不完整子组。
    uint offset = subgroupExclusiveAdd(nodeCount);
    nodeCount = subgroupAdd(nodeCount);
    uint index = 0u;
    if (subgroupElect()) {
        index = atomicAdd(nodeQueueMetadata[queueIdx+1].w, nodeCount);
    #else
    uint index = atomicAdd(nodeQueueMetadata[queueIdx+1].w, nodeCount);
    #endif
    // 按新增条目对向上取整工作组数的贡献预留空间。
    // 原子预留将队列划分为互不重叠的范围。
    // 按完成顺序累加各次预留的工作组增量。
    uint inc = ((index+nodeCount+LOCAL_SIZE-1)>>LOCAL_SIZE_BITS)-((index+LOCAL_SIZE-1)>>LOCAL_SIZE_BITS);
    if (inc != 0) atomicAdd(nodeQueueMetadata[queueIdx+1].x, inc);
    #ifdef SUBGROUP_QUEUE
    }
    nodePushIndex = subgroupBroadcastFirst(index) + offset;
    #else
    nodePushIndex = index;
    #endif
}

void pushNode(uint nodeId) {
    nodeQueueSink[nodePushIndex++] = nodeId;
}

#define SIMPLE_QUEUE(type, name, bindingIndex) layout(binding = bindingIndex, std430) restrict buffer name##Struct { \
    type name##Index; \
    type##[] name; \
};

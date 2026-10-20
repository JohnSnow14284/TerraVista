void addRequest(inout UnpackedNode node) {

    if (!hasRequested(node)) {
        if (requestQueueIndex.x < requestQueueSize) {// 检查请求队列软上限。
            uint atomRes = atomicAdd(requestQueueIndex.x, 1);
            if (atomRes < MAX_REQUEST_QUEUE_SIZE) {// 检查请求队列硬上限。
                // 登记已提交请求，避免重复提交。
                requestQueue[atomRes] = getRawPos(node);
                markRequested(node);
            }
        }
    }
}

void enqueueChildren(in UnpackedNode node) {
    if (childListIsEmpty(node)) {// 对空子列表直接结束处理。
        return;
    }

    uint children = getChildCount(node);
    pushNodesInit(children);
    uint ptr = getChildPtr(node);
    for (int i = 0; i < children; i++) {
        pushNode(ptr+i);
    }
}

void enqueueSelfForRender(in UnpackedNode node) {

    if (renderQueueIndex < renderQueueMaxSize) {
        lastRenderFrame[getId(node)] = frameId;
        if (!isEmptyMesh(node)) {
            uint renderIndex = atomicAdd(renderQueueIndex, 1);
            if (renderIndex < renderQueueMaxSize) {
                renderQueue[renderIndex] = getMesh(node);

            }
        }
    }
}


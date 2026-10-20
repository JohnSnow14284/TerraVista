vec3 closestPointToCamera(in UnpackedNode node) {
    vec3 nPos = vec3(((node.pos<<node.lodLevel)-camSecPos)<<5)-camSubSecPos;
    float scale = 1<<(node.lodLevel+5);
    return mix(mix(vec3(0), nPos, greaterThan(nPos, vec3(0))), nPos+scale, lessThan(nPos+scale, vec3(0)));
}

vec3 furthestPointToCamera(in UnpackedNode node) {
    vec3 nPos = vec3(((node.pos<<node.lodLevel)-camSecPos)<<5)-camSubSecPos;
    float scale = 1<<(node.lodLevel+5);
    return mix(nPos+scale, mix(nPos, vec3(0), greaterThan(nPos, vec3(0))), lessThan(nPos+scale, vec3(0)));
}

bool isWithinRenderDistance(in UnpackedNode node) {
    if (renderDistance<0.0f) return true;
    vec3 close = closestPointToCamera(node);
    float xzDist = close.x*close.x+close.z*close.z;
    return xzDist <= renderDistance;
}

void traverse(in UnpackedNode node) {

    // 计算节点屏幕空间状态。
    setupScreenspace(node);

    if (outsideFrustum() || isCulledByHiz()) {

    } else {
        // 处理当前可见节点。

        // 仅对非根节点继续下降。
        if (node.lodLevel!=0 && shouldDecend()) {
            if (hasChildren(node)) {

                enqueueChildren(node);
            } else {

                addRequest(node);

                // 使用叶节点自身的网格。

                bool shouldRenderSelf = renderDistance<0.0f;
                if (!shouldRenderSelf) {
                    vec3 far = furthestPointToCamera(node);
                    shouldRenderSelf = (far.x*far.x+far.z*far.z+(16*16*16))<=renderDistance;// 额外预留十六乘十六个条目。
                }

                // 跨越视点平面的粗网格等待子级细化。
                if (shouldRenderSelf && !_crossesEyePlane) {
                    enqueueSelfForRender(node);
                }
            }
        } else {
            if (hasMesh(node)) {

                enqueueSelfForRender(node);
            } else {

                // 需要绘制但缺少网格时检查请求状态。
                // 为尚未提交请求的节点发起网格请求。
                addRequest(node);

                if (node.lodLevel != 0) {
                    enqueueChildren(node);
                }
            }
        }
    }
}


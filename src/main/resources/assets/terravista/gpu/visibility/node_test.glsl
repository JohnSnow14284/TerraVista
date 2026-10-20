// 根据视锥与屏幕边界判断隐式剔除。
bool outsideFrustum() {
    return _frustumCulled;// 以十六作为近端齐次深度门限。

}

bool isCulledByHiz() {
    if (_crossesEyePlane) return false;

    if (any(lessThan(abs(_maxBB.xy-_minBB.xy-vec2(1.0f)), vec2(0.000001f)))) return false;

    ivec2 ssize = ivec2(packedHizSize>>16,packedHizSize&0xFFFF);
    vec2 size = (_maxBB.xy-_minBB.xy)*ssize;
    float miplevel = log2(max(max(size.x, size.y),1));

    miplevel = floor(miplevel)-1;

    miplevel = clamp(miplevel, 0, textureQueryLevels(hizDepthSampler)-1);

    int ml = int(miplevel);
    ssize = max(ivec2(1), ssize>>ml);
    ivec2 mxbb = min(ivec2(ceil(_maxBB.xy*ssize)),ssize-1);
    ivec2 mnbb = ivec2(floor(_minBB.xy*ssize));

    float pointSample = (NEAR*3.0f)-1.0f;

    for (int x = mnbb.x; x<=mxbb.x; x++) {
        for (int y = mnbb.y; y<=mxbb.y; y++) {
            float sp = texelFetch(hizDepthSampler, ivec2(x, y), ml).r;

            pointSample = REDUCTION(sp, pointSample);
        }
    }

    float depthTestAgainst;
    #ifdef USE_REVERSE_Z
    depthTestAgainst = _maxBB.z;
    #else
    depthTestAgainst = _minBB.z;
    #endif
    return DEPTH_SCALAR_COMPARE_EQUAL(pointSample,depthTestAgainst);
}

// 判断是否继续下降到子节点。
bool shouldDecend() {
    if (_screenSize > minSSS) return true;
    // 为地平线处细长的投影保留细分机会。
    vec2 span = _maxBB.xy - _minBB.xy;
    float longest = max(span.x, span.y);
    return longest * longest * (1.0 / 16.0) > minSSS;
}

struct Frustum {
    vec4 planes[6];
};

bool testPlane(vec4 plane, vec3 base, float size) {
    return dot(plane.xyz, base+mix(vec3(size), vec3(0), lessThan(plane.xyz, vec3(0)))) >= -plane.w;
}

// 按平面与包围盒位置、尺寸判断视锥相交。
bool outsideFrustum(in Frustum frustum, vec3 pos, float size) {
    return !(testPlane(frustum.planes[0], pos, size) && testPlane(frustum.planes[1], pos, size) &&
           testPlane(frustum.planes[2], pos, size) && testPlane(frustum.planes[3], pos, size) &&
           testPlane(frustum.planes[4], pos, size));// 跳过远平面测试。
}

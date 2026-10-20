#ifndef TERRAVISTA_CURVATURE
#define TERRAVISTA_CURVATURE

// 对相机相对坐标应用统一的有符号曲率。
vec3 curvePoint(vec3 point, vec2 curve) {
    point.y -= dot(point.xz, point.xz) * curve.x;
    return point;
}

// 保守包围曲面，并维持遍历使用的立方体尺寸。
void curveBounds(inout vec3 base, inout vec3 size, vec2 curve) {
    if (curve.x == 0.0) return;
    vec3 end = base + size;
    vec2 nearXZ = max(max(base.xz, -end.xz), vec2(0.0));
    vec2 farXZ = max(abs(base.xz), abs(end.xz));
    vec2 offsets = -curve.x * vec2(dot(nearXZ, nearXZ), dot(farXZ, farXZ));
    float margin = 0.001 + max(abs(offsets.x), abs(offsets.y)) * 0.000002;
    base.y += min(offsets.x, offsets.y) - margin;
    end.y += max(offsets.x, offsets.y) + margin;
    vec3 center = (base + end) * 0.5;
    float extent = max(max(end.x - base.x, end.y - base.y), end.z - base.z);
    size = vec3(extent);
    base = center - size * 0.5;
}

#endif

#include "format/position.glsl"
#include "surface/lightmap.glsl"
// 解码四边形并计算其派生数据。

vec3 swizzelDataAxis(uint axis, vec3 data) {
    return mix(mix(data.zxy,data.xzy,bvec3(axis==0)),data,bvec3(axis==1));
}

vec4 getFaceSize(uint faceData) {
    float EPSILON = 0.00005f;

    vec4 faceOffsetsSizes = extractFaceSizes(faceData);

    // 微量扩展四边形边界。
    faceOffsetsSizes.xz -= vec2(EPSILON);

    // 将终点转换为相对起点的偏移。
    faceOffsetsSizes.yw -= faceOffsetsSizes.xz;

    return faceOffsetsSizes;
}

vec2 taaOffset = vec2(0);

struct QuadData {
    uvec4 attributeData;

    float lodScale;
    uint axis;
    // 计算四边形的四个角点。
    vec3 basePoint;
    vec2 quadSizeAddin;
    vec2 uvCorner;
    vec2 uvSizeAddin;
    uint face;
    uint fluidCornerHeights;
    float fluidDatumOffset;
    float cellBottom;
    float surfaceScale;
    float surfaceOffset;
    float coarseFluidTop;
    bool preciseFluid;
};


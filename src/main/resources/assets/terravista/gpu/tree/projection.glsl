
// 计算 Hi-Z 剔除、屏幕包围盒与投影尺寸。
// 据此判断是否访问子节点。
// 向遍历逻辑提供后续操作条件。

#include "depth/convention.glsl"

layout(binding = HIZ_BINDING) uniform sampler2D hizDepthSampler;

bool within(vec2 a, vec2 b, vec2 c) {
    return all(lessThan(a,b)) && all(lessThan(b, c));
}

bool within(vec3 a, vec3 b, vec3 c) {
    return all(lessThan(a,b)) && all(lessThan(b, c));
}

bool within(float a, float b, float c) {
    return a<b && b<c;
}

float crossMag(vec2 a, vec2 b) {
    return abs(a.x*b.y-b.x*a.y);
}

bool checkPointInView(vec4 point) {
    return within(vec3(-point.w,-point.w,0.0f), point.xyz, vec3(point.w));
}

vec3 _minBB = vec3(0.0f);
vec3 _maxBB = vec3(0.0f);
bool _frustumCulled = false;
bool _crossesEyePlane = false;

float _screenSize = 0.0f;

UnpackedNode node22;
// 准备节点屏幕空间数据并返回是否继续处理。
// 读取主遍历已准备的共享数据。
void setupScreenspace(in UnpackedNode node) {

    node22 = node;

    vec3 basePos = vec3(((node.pos<<node.lodLevel)-camSecPos)<<5)-camSubSecPos;

    vec3 boxSize = vec3(32<<node.lodLevel);
    curveBounds(basePos, boxSize, worldCurveData);
    _frustumCulled = outsideFrustum(frustum, basePos, boxSize.x);

    // 提前结束无效情况。
    if (_frustumCulled) {
        return;
    }

    vec4 P000 = MVP * vec4(basePos, 1);
    mat3x4 Axis = mat3x4(MVP)*boxSize.x;
    vec4 P100 = Axis[0] + P000;
    vec4 P001 = Axis[2] + P000;
    vec4 P101 = Axis[2] + P100;
    vec4 P010 = Axis[1] + P000;
    vec4 P110 = Axis[1] + P100;
    vec4 P011 = Axis[1] + P001;
    vec4 P111 = Axis[1] + P101;

    // 包围盒跨越零齐次深度时保留可见性并继续细分。
    // 对子节点继续执行视锥测试。
    float minW = min(min(min(P000.w, P100.w), min(P001.w, P101.w)),
                     min(min(P010.w, P110.w), min(P011.w, P111.w)));
    _crossesEyePlane = minW <= 0.00001;
    if (_crossesEyePlane) {
        _screenSize = 3.402823466e+38;
        return;
    }

    // 执行透视除法并转换到归一化屏幕坐标。
    vec3 p000 = NDC2SCREEN(P000.xyz/P000.w);
    vec3 p100 = NDC2SCREEN(P100.xyz/P100.w);
    vec3 p001 = NDC2SCREEN(P001.xyz/P001.w);
    vec3 p101 = NDC2SCREEN(P101.xyz/P101.w);
    vec3 p010 = NDC2SCREEN(P010.xyz/P010.w);
    vec3 p110 = NDC2SCREEN(P110.xyz/P110.w);
    vec3 p011 = NDC2SCREEN(P011.xyz/P011.w);
    vec3 p111 = NDC2SCREEN(P111.xyz/P111.w);

    {// 计算屏幕覆盖面积。
        float ssize = 0;
        {// 累加从零角点出发的三个面。

            vec2 A = p100.xy-p000.xy;
            vec2 B = p010.xy-p000.xy;
            vec2 C = p001.xy-p000.xy;
            ssize += crossMag(A,B);
            ssize += crossMag(A,C);
            ssize += crossMag(C,B);
        }
        {// 累加从对角点出发的三个面。
            vec2 A = p011.xy-p111.xy;
            vec2 B = p101.xy-p111.xy;
            vec2 C = p110.xy-p111.xy;
            ssize += crossMag(A,B);
            ssize += crossMag(A,C);
            ssize += crossMag(C,B);
        }
        ssize *= 0.5f;// 将前后面的总面积减半。
        _screenSize = ssize;
    }

    _minBB = min(min(min(p000, p100), min(p001, p101)), min(min(p010, p110), min(p011, p111)));
    _maxBB = max(max(max(p000, p100), max(p001, p101)), max(max(p010, p110), max(p011, p111)));

    _minBB = clamp(_minBB, vec3(0), vec3(1));
    _maxBB = clamp(_maxBB, vec3(0), vec3(1));
}


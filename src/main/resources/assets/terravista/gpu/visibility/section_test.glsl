#include "depth/convention.glsl"
layout(binding = HIZ_BINDING) uniform sampler2D hiz;
layout(push_constant) uniform CullOptions { uint refine; };

bool occluded(SectionMeta meta) {
    uint detail = extractDetail(meta);
    vec3 base = vec3(((extractPosition(meta)<<detail)-baseSectionPos)<<5);
    vec3 lo = vec3(extractAABBOffset(meta))-1.0;
    vec3 size = vec3(extractAABBSize(meta))+2.0;
    vec3 boxBase = base + lo * float(1u<<detail) - cameraSubPos;
    vec3 boxSize = size * float(1u<<detail);
    curveBounds(boxBase, boxSize, worldCurveData);
    vec2 lower = vec2(1.0), upper = vec2(0.0);
    float nearest = FAR;
    for (int i=0;i<8;i++) {
        vec3 corner = boxBase + vec3(i&1,(i>>1)&1,(i>>2)&1)*boxSize + cameraSubPos;
        vec4 clip = MVP*vec4(corner,1.0);
        // 跨越眼平面或裁剪面的包围盒保持可见。
        // 避免透视除法将包围盒错误缩小。
        if (any(isnan(clip)) || any(isinf(clip)) || clip.w<=0.00001) return false;
        vec3 p = NDC2SCREEN(clip.xyz/clip.w);
        if (p.z<=0.0 || p.z>=1.0) return false;
        lower=min(lower,p.xy); upper=max(upper,p.xy);
        nearest=REDUCTION2(nearest,p.z);
    }
    // 屏幕外与边界情况沿用遍历结果。
    if (any(lessThan(lower,vec2(0))) || any(greaterThan(upper,vec2(1)))) return false;
    vec2 baseSize=vec2(textureSize(hiz,0));
    lower=max(vec2(0),lower-1.0/baseSize);
    upper=min(vec2(1),upper+1.0/baseSize);
    vec2 extent=(upper-lower)*baseSize;
    int level=clamp(int(ceil(log2(max(max(extent.x,extent.y),1.0)))),0,textureQueryLevels(hiz)-1);
    ivec2 dims=textureSize(hiz,level);
    ivec2 first=clamp(ivec2(floor(lower*vec2(dims))),ivec2(0),dims-1);
    ivec2 last=clamp(ivec2(floor(upper*vec2(dims))),ivec2(0),dims-1);
    float farthest=NEAR;
    for(int y=first.y;y<=last.y;y++) for(int x=first.x;x<=last.x;x++) {
        float d=texelFetch(hiz,ivec2(x,y),level).r;
        if(isnan(d) || isinf(d)) return false;
        farthest=REDUCTION(farthest,d);
    }
    // 通过严格分离条件保留共面几何。
    return CLOSER_SIGN*(farthest-nearest)>0.00001;
}


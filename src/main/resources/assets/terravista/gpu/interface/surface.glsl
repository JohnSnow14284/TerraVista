layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    uint frameId;
    vec3 cameraSubPos;
    float seaSurfaceY;
    vec2 worldCurveData;
};

#include "surface/curvature.glsl"

struct DrawCommand {
    uint  count;
    uint  instanceCount;
    uint  firstIndex;
    int  baseVertex;
    uint  baseInstance;
};

#ifdef BLOCK_MODEL_TEXTURE_BINDING
layout(binding = BLOCK_MODEL_TEXTURE_BINDING) uniform sampler2D blockModelAtlas;
#endif

#ifndef Quad
#define Quad ivec2
#endif
#ifdef QUAD_BUFFER_BINDING
layout(binding = QUAD_BUFFER_BINDING, std430) readonly restrict buffer QuadBuffer {
    Quad quadData[];
};
#endif


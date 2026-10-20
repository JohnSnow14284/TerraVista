#version 460 core
// 使用双字四边形格式读取数据。
// 仅使用宿主已启用的设备特性。

#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#define LIGHTING_SAMPLER_BINDING 9
#define VERT_ID gl_VertexIndex
#define BASE_INSTANCE gl_InstanceIndex

#include "format/quad.glsl"
#include "format/material.glsl"
#include "interface/surface.glsl"
#include "interface/commands.glsl"
#include "interface/residency.glsl"
#include "surface/quad_input.glsl"
#include "surface/quad_attributes.glsl"
#include "surface/quad_geometry.glsl"

layout(location = 0) out flat uvec4 interData;
layout(location = 1) out vec2 uv;

vec2 taaShift();

void main() {
    taaOffset = taaShift();

    QuadData quad;
    uvec2 pos = positionBuffer[BASE_INSTANCE];
    setupQuad(quad, quadData[uint(VERT_ID)>>2], pos, (VERT_ID&3) == 1);

    uint cornerId = VERT_ID&3;

    gl_Position =
    (getQuadCornerPos(quad, cornerId));

    uv = getCornerUV(quad, cornerId);

    // 仅输出已生成的顶点数据。
    interData = quad.attributeData;

}

vec2 taaShift() {return vec2(0.0);}

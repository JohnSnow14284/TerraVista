#version 460

#include "depth/convention.glsl"

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec4 cameraBlockPos;
    vec4 negInnerBlock;
};

layout(binding = 1, std430) restrict readonly buffer ChunkPosBuffer {
    ivec2[] chunkPos;
};

ivec3 unpackPos(ivec2 pos) {
    return ivec3(pos.y>>10, (pos.x<<12)>>12, ((pos.y<<22)|int(uint(pos.x)>>10))>>10);
}

bool shouldRender(ivec3 icorner) {
    vec3 corner = vec3(mix(mix(ivec3(0), icorner-1, greaterThan(icorner-1, ivec3(0))), icorner+17, lessThan(icorner+17, ivec3(0))))-negInnerBlock.xyz;
    bool visible = (corner.x*corner.x + corner.z*corner.z) < (negInnerBlock.w*negInnerBlock.w);
    visible = visible && abs(corner.y) < negInnerBlock.w;
    return visible;
}

// 每个实例以三十六个索引绘制一个边框盒。
// 实例索引直接表示区块编号，顶点索引表示八个角点。
#define VERT_ID gl_VertexIndex

void main() {
    uint id = uint(gl_InstanceIndex);

    ivec3 origin = unpackPos(chunkPos[id])*16;
    origin -= cameraBlockPos.xyz;

    if (!shouldRender(origin)) {
        gl_Position = vec4(-100.0f, -100.0f, -100.0f, 0.0f);
        return;
    }

    ivec3 cubeCornerI = ivec3(VERT_ID&1, (VERT_ID>>2)&1, (VERT_ID>>1)&1)*16;
    // 将边框沿 Y 轴扩展到正负 8192。

    gl_Position = MVP * vec4(vec3(cubeCornerI+origin), 1);

    gl_Position.z += CLOSER_SIGN*0.0005f;// 沿深度方向将边框移近相机。

}

// 清理深度转换宏。
#include "depth/release.glsl"

#version 460 core

layout(binding = 8) uniform sampler2D blockModelAtlas;
layout(binding = 10) uniform sampler2D depthTex;

layout(location = 0) in flat uvec4 interData;
layout(location = 1) in vec2 uv;

// 通过零采样掩码处理需要裁剪的片元。

// 其余路径执行常规片元丢弃。
#ifdef TERRAVISTA_VULKAN_SAMPLE_MASK_DISCARD
#define TERRAVISTA_DISCARD_FRAGMENT() { gl_SampleMask[0] = 0; return; }
#else
#define TERRAVISTA_DISCARD_FRAGMENT() { discard; return; }
#endif

layout(location = 0) out vec4 outColour;

#include "interface/surface.glsl"
#include "interface/commands.glsl"
#include "interface/residency.glsl"
#include "surface/lightmap.glsl"

#include "depth/convention.glsl"

#include "surface/colour.glsl"

void main() {
    #ifdef TERRAVISTA_VULKAN_SAMPLE_MASK_DISCARD
    gl_SampleMask[0] = 1;
    #endif

    // 定位当前纹理单元。
    vec2 tile;

    vec2 uv2 = modf(uv, tile)*(1.0/(vec2(3.0,2.0)*256.0));
    vec4 colour;
    vec2 texPos = uv2 + getBaseUV();

    {
        vec2 uvSmol = uv*(1.0/(vec2(3.0,2.0)*256.0));
        vec2 dx = dFdx(uvSmol);
        vec2 dy = dFdy(uvSmol);
        colour = textureGrad(blockModelAtlas, texPos, dx, dy);
    }

    // 计算导数后结束辅助调用。
    // 保留导数计算所需的调用参与范围。

    #if !defined(TERRAVISTA_VULKAN_SAMPLE_MASK_DISCARD)
    if (gl_HelperInvocation) {
        return;
    }
    #endif

    #ifdef TRANSLUCENT
    uint face = (interData.x >> 4) & 7u;
    if ((interData.w & (1u << 13)) != 0u
            && (face & 1u) == uint(gl_FrontFacing != ((face >> 1) != 0u))) {
        TERRAVISTA_DISCARD_FRAGMENT();
    }
    #endif

    if (any(notEqual(clamp(tile, vec2(0), vec2((interData.x>>8)&0xFu, (interData.x>>12)&0xFu)), tile))) {
        TERRAVISTA_DISCARD_FRAGMENT();
    }

    // 检查纹理最小边界与裁剪阈值。
    if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {
        TERRAVISTA_DISCARD_FRAGMENT();
    }

    #ifndef TRANSLUCENT
    uint foliage = (interData.w>>11)&3u;
    float cutoutAlpha = foliage == 2u ? colour.a : textureLod(blockModelAtlas, texPos, 0).a;
    colour.a = 1.0f;
    if (foliage != 1u && useDiscard() && cutoutAlpha <= (foliage == 2u ? 0.35f : 0.1f)) {

    #else
    if (textureLod(blockModelAtlas, texPos, 0).a == 0.0f) {
    #endif

        TERRAVISTA_DISCARD_FRAGMENT();
    }

    #if !defined(TERRAVISTA_VULKAN_SAMPLE_MASK_DISCARD)
    if (gl_HelperInvocation) {
        return;
    }
    #endif

    colour = computeColour(texPos, colour);
    outColour = colour;

}

// 清理深度转换宏。
#include "depth/release.glsl"

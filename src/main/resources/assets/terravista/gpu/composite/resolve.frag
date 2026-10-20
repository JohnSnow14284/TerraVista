#version 450 core

layout(binding = 0) uniform sampler2D depthTex;
layout(binding = 1, std140) uniform CompositeParams {
    mat4 invProjMat;
    mat4 projMat;
    vec4 endParams;
    vec4 fogColour;
};
#ifdef EMIT_COLOUR
layout(binding = 3) uniform sampler2D colourTex;
#endif

#include "depth/convention.glsl"

out vec4 colour;
in vec2 UV;

vec3 rev3d(vec3 clip) {
    vec4 view = invProjMat * vec4(SCREEN2NDC(clip),1.0f);
    return view.xyz/view.w;
}

float projDepth(vec3 pos) {
    vec4 view = projMat * vec4(pos, 1);
    return view.z/view.w;
}

void main() {
    float depth = texture(depthTex, UV.xy).r;
    if (depth == 0.0f || depth == 1.0f) {
        discard;
    }

    vec3 point = rev3d(vec3(UV.xy, depth));
    depth = projDepth(point);
    // 输出采样深度。
    depth = REDUCTION2(FAR+CLOSER_SIGN*(2.0f/((1<<24)-1)), depth);
    depth = NDC2SCREEN_DEPTH(depth);

    gl_FragDepth = depth;

    #ifdef EMIT_COLOUR
    colour = texture(colourTex, UV.xy);
    if (colour.a == 0.0) {
        discard;
    }
    #ifdef HAS_FOG
    if (fogColour.a>0.0){
        float fogLerp = clamp(fma(length(point.xyz),endParams.x,endParams.y),0,endParams.z);// 以五百一十二方块表示三十二乘十六的距离范围。
        colour.rgb = mix(colour.rgb, fogColour.rgb, fogLerp*fogColour.a);
    }
    #endif
    #ifdef HAS_FADE

    if (fadeParams.x>0) {
        float len = fadeParams.x>1.5?length(point.xyz):length(point.xz);
        colour.a *= 1-clamp(fma(len, fadeParams.z, fadeParams.y), 0,1);
    }

    #endif
    #else
    colour = vec4(0);
    #endif

}

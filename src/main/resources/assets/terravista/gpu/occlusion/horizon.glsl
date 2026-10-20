#ifdef BETTER_SSAO
#include "occlusion/samples.glsl"

mat3 get_tbn_matrix(vec3 normal) {
    // 竖直法线平行于参考轴时使用备用切线。
    // 避免归一化零叉积。
    vec3 tangent = abs(normal.y) == 1.0
    ? vec3(1.0, 0.0, 0.0)
    : normalize(cross(vec3(0.0, 1.0, 0.0), normal));
    vec3 bitangent = normalize(cross(tangent, normal));
    return mat3(tangent, bitangent, normal);
}

vec2 clamp_length(vec2 a, float min, float max) {
    // 处理与视线平行的采样方向。
    if (all(equal(a, vec2(0.0)))) return vec2(0.0);
    return normalize(a)*clamp(length(a), min, max);
}
vec3 projProj(vec3 point) {
    vec4 aa = Proj*vec4(point,1);
    return NDC2SCREEN(aa.xyz/aa.w);
}

#ifdef USE_GENERATED_SAMPLE_POINTS
const vec2 SAMPLE_POINTS[SSAO_STEPS] = vec2[SSAO_STEPS](%%CONST_ARRAY%%);
#endif
float computeAO(vec3 uvDepth, vec3 worldNorm)
{
    vec3 position_view = rev3d(invProj, uvDepth);
    vec3 viewNormal = normalize(mat3(MV)*worldNorm);

    mat3 tbn_matrix = get_tbn_matrix(viewNormal);

    mat2 sample_matrix = SSAO_RADIUS *
        mat2(clamp_length(
            projProj(
                position_view + tbn_matrix[0])
            .xy -
            uvDepth.xy,
            0.0,
            SSAO_MAX_RADIUS_SCREEN
        ),
        clamp_length(
            projProj(
                position_view + tbn_matrix[1]
            )
            .xy -
            uvDepth.xy,
            0.0,
            SSAO_MAX_RADIUS_SCREEN
        ));

    float ao = 0.0;

    for (int i = 0; i < SSAO_STEPS; ++i) {

        #ifdef USE_GENERATED_SAMPLE_POINTS
        vec2 sample_uv = clamp(uvDepth.xy + sample_matrix * SAMPLE_POINTS[i], vec2(0), vec2(1));
        #else
        vec2 sample_uv = clamp(uvDepth.xy + sample_matrix*get_ssao_sample_offset(i, vec2(0.5f)), vec2(0), vec2(1));

        #endif
        sample_uv = (vec2(ivec2(sample_uv*size))+0.5)*scale;
        float depth = texture(depthTex, sample_uv).x;

        if (depth == FAR || depth == uvDepth.z) {
            continue;
        }
        vec3 offset_view;
        if (depth == NEAR) {// 按原版深度路径采样。
            depth = texture(sourceDepthTex, sample_uv).x;
            if (depth == FAR) {
                continue;
            }
            offset_view = rev3d(sourceInvProj, vec3(sample_uv, depth));
        } else {
            uint meta = uint(255.0f*texture(colourTex, sample_uv).w);
            if ((meta&(1<<6))==0) {
                continue;
            }
            offset_view = rev3d(invProj, vec3(sample_uv, depth));
        }
        offset_view -= position_view;

        float slen = dot(offset_view,offset_view);

        float rlen = inversesqrt(slen);
        float angle = dot(offset_view, viewNormal)*rlen;
        float cos_theta = clamp(angle, 0, 1);
        float distance_falloff = 1.0f/(1.0 + (1.0f/rlen) * (1.0f/(float(SSAO_RADIUS))));

        ao += cos_theta * distance_falloff;
    }

    return pow(clamp(1.0 - ao * (1.0f/(float(SSAO_STEPS))),0,1),2.5f);
}

#endif


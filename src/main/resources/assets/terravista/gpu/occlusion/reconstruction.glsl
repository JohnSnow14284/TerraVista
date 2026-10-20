vec3 rev3d(mat4 matrix, vec3 clip) {
    vec4 view = matrix * vec4(SCREEN2NDC(clip),1.0);
    return view.xyz/view.w;
}

ivec2 size;
vec2 scale;

#ifndef BETTER_SSAO
vec4 reDeProject(vec3 pos) {
    vec4 view = MVP * vec4(pos, 1.0);
    view.xy /= view.w;
    vec2 samplePos = view.xy*0.5+0.5;// 将投影平面坐标转换到纹理采样范围。
    samplePos = clamp((vec2(ivec2(samplePos*size))+0.5)*scale, vec2(0.0f), vec2(1.0f));
    view.xy = samplePos;

    float depth = texture(depthTex, view.xy).x;

    if (depth == 1.0f || depth == 0.0f) {
        return vec4(-1.0f);
    }

    uint meta = uint(255.0f*texture(colourTex, view.xy).w);
    if ((meta>>6)==0) {
        return vec4(-1.0f);
    }

    view.xy = view.xy*2.0f-1.0f;
    view.z = SCREEN2NDC_DEPTH(depth);
    view.w = 1.0;
    view = invMVP * view;
    return vec4(view.xyz/view.w, 1.0f);
}

float computeAO(vec3 uvDepth, vec3 normal) {
    const float offset = 0.5f;
    vec3 pos = rev3d(invMVP, uvDepth);
    vec4 reproData = reDeProject(pos+normal*offset);
    if (reproData.w < 0.0f) {
        return 1.0f;
    }
    vec3 repro = reproData.xyz - pos;
    float len = length(repro);
    if (len > offset+2.0f) {
        return 1.0f;
    }
    if (len <0.01f) {
        return 1.0f;
    }
    float ao = dot(repro, normal)/len;
    if (ao <0.01f) {
        return 1.0f;
    }

    ao = pow(ao,0.15f);

    return clamp(((1.0f-ao)/3.0f+(2.0f/3.0f)), 0,1);
}
#endif


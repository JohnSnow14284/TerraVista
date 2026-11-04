// Vitrail supplies the matrices; TerraVista's packed prologue supplies unlit material data.
#include "/lib/settings.glsl"
#include "/lib/res_params.glsl"
#define DH_TAA_OVERRIDE
#include "/lib/TAA_jitter.glsl"
uniform mat4 dhProjection;
uniform mat4 gbufferModelView;
uniform vec2 texelSize;
uniform int framemod4_DH;
out vec4 tvLodColour;
out vec4 tvLodTint;
out vec2 tvLodLight;
flat out uint tvLodFace;
flat out uint tvLodId;
void main() {
    gl_Position = dhProjection * gbufferModelView * gl_Vertex;
#ifdef TAA_UPSCALING
    gl_Position.xy = gl_Position.xy * RENDER_SCALE + RENDER_SCALE * gl_Position.w - gl_Position.w;
#endif
#ifdef TAA
    gl_Position.xy += offsets[framemod4_DH] * texelSize * gl_Position.w;
#endif
    tvLodColour = terravistaBaseColour;
    tvLodTint = terravistaTint;
    tvLodFace = terravistaFace;
    tvLodId = terravistaCustomId;
    tvLodLight = gl_MultiTexCoord1.xy;
}

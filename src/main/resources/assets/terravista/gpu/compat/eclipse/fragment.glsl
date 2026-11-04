// TV pack inputs use Vitrail's Vulkan distant projection/depth contract.
#define tvProjection dhProjection
#define tvProjectionInverse dhProjectionInverse
#define tvPreviousProjection dhPreviousProjection
#define tvModelView gbufferModelView
#define tvModelViewInverse gbufferModelViewInverse
#define tvPreviousModelView gbufferPreviousModelView
#define tvDepthTexOpaque dhDepthTex1
uniform mat4 dhProjection;
uniform mat4 dhProjectionInverse;
uniform mat4 dhPreviousProjection;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferPreviousProjection;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowProjectionInverse;
uniform sampler2D dhDepthTex1;
uniform sampler2D noisetex;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform float viewWidth;
uniform float viewHeight;
uniform float nightVision;
uniform float noPuddleAreas;
uniform int tvRenderDistance;
uniform int worldDay;
uniform int worldTime;
uniform float moonElevation;
uniform float worldTimeSmooth;
uniform float cumulonimbusStrength;
uniform int frameCounter;
uniform int framemod8;
uniform int framemod4_DH;
uniform float tvNearPlane;
uniform float tvFarPlane;
uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;
uniform float near;
uniform float far;
uniform float frameTimeCounter;
uniform vec2 texelSize;
uniform vec3 sunPosition;
uniform float sunElevation;
uniform vec4 lightningBoltPosition;
uniform float thunderStrength;
uniform float rainStrength;
uniform vec3 unsigned_WsunVecSmooth;
uniform vec3 unsigned_WmoonVecSmooth;
uniform int isEyeInWater;
in vec4 tvLodColour;
in vec4 tvLodTint;
in vec2 tvLodLight;
flat in uint tvLodFace;
flat in uint tvLodId;
struct TerraVistaFragmentParameters {
    vec4 sampledColour;
    vec2 tile;
    vec2 uv;
    uint face;
    uint modelId;
    vec2 lightMap;
    vec4 tinting;
    uint customId;
};
#include "/TV_ECLIPSE_ENTRY"
void main() {
    terravista_emitFragment(TerraVistaFragmentParameters(tvLodColour, vec2(0), vec2(0), tvLodFace,
            0u, tvLodLight, tvLodTint, tvLodId));
}

// Input to a pack's dh_terrain / dh_water vertex prologue, before its matrices and jitter.
// Uses the existing 8-byte quad, 64-byte model and per-draw section position buffers.
#define TERRAVISTA_PACK_VERTEX
#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5

// Distinct descriptor names let the pack's own blocks coexist with TerraVista's buffers.
#define SceneUniform TerraVistaScene
#define QuadBuffer TerraVistaQuads
#define ModelBuffer TerraVistaModels
#define ModelColourBuffer TerraVistaColours
#define PositionScratchBuffer TerraVistaPositions

#define NO_SHADE_FACE_TINT 1.0
#define UP_FACE_TINT 1.0
#define DOWN_FACE_TINT 1.0
#define Z_AXIS_FACE_TINT 1.0
#define X_AXIS_FACE_TINT 1.0

#include "format/quad.glsl"
#include "format/material.glsl"
#include "interface/surface.glsl"
#include "interface/residency.glsl"
#include "surface/quad_input.glsl"

// setupQuad's attribute branch is disabled below. A pack applies its own lighting.
vec4 getLighting(uint index) { return vec4(1.0); }
#include "surface/quad_attributes.glsl"
#include "surface/quad_geometry.glsl"

struct TerraVistaDistantVertex {
    vec4 position;
    vec4 colour;
    vec3 normal;
    vec2 light;
    uint material;
};

TerraVistaDistantVertex terravistaDistantVertex() {
    Quad raw = quadData[uint(gl_VertexIndex) >> 2];
    uint face = extractFace(raw);
    BlockModel model = modelData[extractStateId(raw)];
    QuadData quad;
    setupQuad(quad, raw, positionBuffer[gl_InstanceIndex], false);

    TerraVistaDistantVertex vertex;
    vertex.position = getQuadCornerPos(quad, uint(gl_VertexIndex) & 3u);
    vertex.colour = unpackUnorm4x8(model.distantColour[face]);
    uint tint = model.colourTint;
    if (modelHasBiomeLUT(model)) {
        tint = usesBlendedTint(raw) ? colourData[65536u + extractBiomeId(raw)] | 0xff000000u
                : colourData[tint + extractBiomeId(raw)];
    }
    if (faceTintState(model.faceData[face]) != 0u && tint != uint(-1)) {
        vertex.colour.rgb *= vec3((tint >> 16) & 255u, (tint >> 8) & 255u, tint & 255u) / 255.0;
    }
    const vec3 normals[6] = vec3[6](vec3(0,-1,0), vec3(0,1,0), vec3(0,0,-1),
            vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0));
    vertex.normal = normals[face];
    vertex.light = getLightmapUv(extractLightId(raw));
    vertex.material = model.distantMaterial;
    return vertex;
}

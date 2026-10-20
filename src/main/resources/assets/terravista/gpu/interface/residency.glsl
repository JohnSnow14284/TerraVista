#ifndef VISIBILITY_ACCESS
#define VISIBILITY_ACCESS readonly
#endif
#ifdef VISIBILITY_BUFFER_BINDING
layout(binding = VISIBILITY_BUFFER_BINDING, std430) VISIBILITY_ACCESS restrict buffer VisibilityBuffer {
    uint visibilityData[];
};
#endif

#ifdef MODEL_BUFFER_BINDING
layout(binding = MODEL_BUFFER_BINDING, std430) readonly restrict buffer ModelBuffer {
    BlockModel modelData[];
};
#endif

#ifdef MODEL_COLOUR_BUFFER_BINDING
layout(binding = MODEL_COLOUR_BUFFER_BINDING, std430) readonly restrict buffer ModelColourBuffer {
    uint colourData[];
};
#endif

#ifdef POSITION_SCRATCH_BINDING
#ifndef POSITION_SCRATCH_ACCESS
#define POSITION_SCRATCH_ACCESS readonly
#endif
layout(binding = POSITION_SCRATCH_BINDING, std430) POSITION_SCRATCH_ACCESS restrict buffer PositionScratchBuffer {
    uvec2 positionBuffer[];
};
#endif

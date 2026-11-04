// Select Eclipse's TV shading branches. The Vulkan program family stays Vitrail DISTANT.
#define TERRAVISTA 1
#undef DISTANT_HORIZONS
#define tvProjection dhProjection
#define tvProjectionInverse dhProjectionInverse
#define tvPreviousProjection dhPreviousProjection
#define tvModelView gbufferModelView
#define tvModelViewInverse gbufferModelViewInverse
#define tvPreviousModelView gbufferPreviousModelView
#define tvDepthTexOpaque dhDepthTex1
#define tvDepthTex dhDepthTex
// Eclipse's optional cloud clock is not a Vitrail uniform; keep animation in elapsed seconds.
#define cloudTime frameTimeCounter

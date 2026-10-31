# Vitrail integration branch

Status: in development. This branch does not yet provide verified shader-pack LOD support.

Reference: https://github.com/avpbynf/Vitrail-Shaders

Inspected upstream commit: `7a3cdcbe1dfd7e04fb2b3021ee8352d341e7647e` (0.13.0-beta sources).
TerraVista baseline: `aa89e0a`, Minecraft 26.3, NeoForge 26.3.0.76, Java 25.

## Implemented

TerraVista replaces the vanilla main render pass before features and classic transparency.
Vitrail's 26.3 renderer needs those replacement passes registered through
`dev.vitrail.render.LevelPass.open`, otherwise its suspend operation cannot close the
underlying host pass before opening shader passes. Replacement passes now use this wrapper
when Vitrail is present, retaining ordinary passes when absent. The optional method handle
is resolved once, before acquiring the first replacement pass; failed registration closes
the acquired pass. No Vitrail code or binaries are bundled.

## Evidence and remaining requirements

- New TerraVista bridge and its caller compile directly against existing local game dependencies.
- Full Gradle build has not passed: NeoForge Maven dependency downloads timed out.
- Contract verification against the unmodified upstream LevelPass exposed a host API difference:
  RenderPass in the local game requires `setViewport(int,int,int,int)`, which the inspected
  upstream LevelPass does not implement. Upstream versions/26.3.properties targets
  NeoForge 26.3.0.16-beta and Sodium 0.9.2; TerraVista targets a newer host build.
- Closing this API difference and validating suspension, nesting, failure cleanup, pass reopening
  and shader reload is required before enabling a compatibility claim.
- LOD colour, material and depth must reach Vitrail's shader programs at the correct opaque /
  translucent stages. Merely compositing TerraVista's vanilla-lit colour onto the main target
  does not satisfy this requirement. Current bridge changes alone do not implement that path.
- Validate no shader pack, Vitrail shaders off/on, supported packs, resized/scaled targets,
  world transitions and resource reloads in game. Measure CPU/GPU frame time, native buffer and
  VRAM use, and LOD streaming against the same scene without this integration.

External investigation logs and contract checks are under
`D:/MCMod/项目资料/TerraVista/vitrail-adaptation/` and are excluded from the product JAR.

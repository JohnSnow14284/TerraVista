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

The packed distant vertex adapter now produces the inputs Vitrail's DH shader translation expects
from TerraVista's existing buffers. It reuses the ordinary fluid/surface geometry decoder, returns
camera-relative block coordinates before the pack's matrices and jitter, and supplies unlit colour,
normal, normalized block/sky light and DH material category. The existing 64-byte model record's
last 28 bytes hold six face colours and a material category; geometry remains 8 bytes per quad.
Those fields are baked only when the packed Vitrail adapter is selected. Material category participates in model
deduplication so two visually identical models with different pack semantics cannot alias.

Shared shader globals and include macros are isolated from the pack's namespace. Optional Mixins
now install the packed prologue, an empty vertex format and the five storage block declarations
in Vitrail's real program-loading path. The packed scene block uses std430 with the same offsets
as the original scene upload. Vitrail's descriptor mechanism can borrow the existing native
allocations, retaining the full storage range and the scene upload's offset. Host uniform slices
remain 16 bytes; Vitrail supplies the native SSBO descriptor type, handle and range. Facades never
map or free the owner's memory, and cannot bind after that owner closes.

The upstream translation cache includes the `TERRAVISTA_PACKED_LOD` ABI define, keeping DH vertex
attribute translations separate from packed translations. Program adaptation stays off when
Distant Horizons is installed, so its own vertex buffers are not reinterpreted as TerraVista quads.

Indirect submission, distant projection/depth and opaque/translucent scheduling still need to
connect the existing frame renderer to this program-loading and binding path. The product does
not yet advertise `DISTANT_HORIZONS` on TerraVista's behalf.

## Evidence and remaining requirements

- New TerraVista bridge and its caller compile directly against existing local game dependencies.
- Full Gradle build passed using the Windows system proxy and Windows certificate trust.
  The earlier timeouts were caused by Java bypassing the configured system proxy.
- Contract verification against the unmodified upstream LevelPass exposed a host API difference:
  RenderPass in the local game requires `setViewport(int,int,int,int)`, which the inspected
  upstream LevelPass does not implement. Upstream versions/26.3.properties targets
  NeoForge 26.3.0.16-beta and Sodium 0.9.2; TerraVista targets a newer host build.
- An optional Mixin now supplies the missing viewport method and restores an explicitly set
  viewport after Vitrail reopens its real pass. State is allocated lazily per wrapper, not per draw.
  A later upstream implementation of the method takes precedence over this unique method.
- Contract checks passed for the upstream open descriptor, absent-mod identity, suspension,
  nesting, closing, registration failure cleanup and viewport state replay.
- Repeated the pass contract check against the actual built upstream NeoForge JAR; it passed.
- CPU checks passed for unlit linear colour averaging, transparent cutout holes, translucent
  intrinsic alpha, the 64-byte model layout and material-aware deduplication. The additional work
  occurs during model baking, not per frame; its in-game loading cost has not been measured.
- Shaderc compiled 24 packed-input variants using the real upstream distant prologue, including
  optional colour/normal/material inputs, int/uint/float material declarations and conflicting
  pack identifiers/macros. Ordinary terrain SPIR-V remains byte-identical to the pre-adapter
  baseline with optimization both disabled and enabled.
- An external probe substituted the adapter into upstream's distant prologue while translating
  the real Complementary Unbound r5.9.3 archive. Both opaque and water vertex stages compiled to
  Vulkan SPIR-V. This was an off-game translation probe, not a game or graphics-device draw.
  Inspection of all three compiled modules confirmed no location-based vertex inputs and the
  original 64-byte model stride, with face colours at byte 36 and material at byte 60.
- An isolated Minecraft 26.3 / NeoForge 26.3.0.76 / Sodium 0.9.3 / Vitrail client started on the
  native Vulkan device and exited normally. The probe confirmed actual application of the
  viewport, storage, packed vertex, vertex format, program declaration and cache ABI Mixins.
  The first cache check exposed an injection targeting the wrong `table` overload; its descriptor
  is now explicit, and the corrected check passed.
- In that client, a probe passed real Vulkan allocations through the actual storage Mixins into
  a capturing RenderPass. Handles, ranges, changed scene offsets and cached Bound identity
  matched; binding allocated no extra TerraVista GPU buffers and all probe allocations retired.
  This checks binding/lifetime glue, not Vulkan descriptor pushing or a rendered LOD draw.
- The same client translated and compiled Complementary's opaque and water vertex programs
  through the actual packed-prologue Mixin. The probe temporarily enabled the DH preprocessor
  environment for those translations and restored it afterward. It opened no world; these
  results do not prove in-world pipeline creation, depth publication, LOD appearance or FPS.
- Face colour is a flat LOD approximation. Conditional tint masks currently tint the averaged
  face as a whole; material classification covers common vanilla categories and falls back to
  unknown for other blocks. Validate mixed-tint faces and modded materials before release.
- In-world pass reopening and shader reload remain required.
- Packed quad winding/culling must be validated against the pack pipeline rather than assuming
  DH's mesh convention. Keep staged GPU resources alive through the later water draw before
  retiring the frame's upload ranges.
- The client without Vitrail also started and exited normally: optional Mixins were skipped,
  packed adaptation remained disabled and the raw pass was returned unchanged. No world opened.
- LOD colour, material and depth must reach Vitrail's shader programs at the correct opaque /
  translucent stages. Merely compositing TerraVista's vanilla-lit colour onto the main target
  does not satisfy this requirement. Current bridge changes alone do not implement that path.
- Upstream DistantDraw.draw accepts DH section buffers at a 16-byte vertex stride, while
  TerraVista stores a quad in 8 bytes and pulls vertices on the GPU. Expanding four vertices
  per quad to that format would increase raw geometry storage eightfold before indices;
  it is not the intended high-performance integration. Preserve packed geometry and indirect
  draws while adapting the pack vertex prologue, render targets and distant-depth publication.
- Validate no shader pack, Vitrail shaders off/on, supported packs, resized/scaled targets,
  world transitions and resource reloads in game. Measure CPU/GPU frame time, native buffer and
  VRAM use, and LOD streaming against the same scene without this integration.

External investigation logs and contract checks are under
`D:/MCMod/项目资料/TerraVista/vitrail-adaptation/` and are excluded from the product JAR.

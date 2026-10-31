# TerraVista + Vitrail Vulkan integration

The rendering correspondence is deliberately split by graphics backend:

| Backend | World LOD renderer | Shader integration |
| --- | --- | --- |
| OpenGL | Voxy | Iris |
| Vulkan | TerraVista | Vitrail |

This branch implements the Vulkan row in TerraVista. The Vitrail repository and its shader-pack
source are not patched; TerraVista adapts its own LOD buffers, indirect commands, depth contract,
and frame lifetime to Vitrail's existing distant passes.

Status: implementation branch. Complementary and Bliss distant programs render packed LODs in real
Vulkan worlds. A legacy pack without distant programs also renders through the basic-colour
fallback. Shader toggles, scaling, resource reloads and staging-bank rotation passed on the RTX
4070 Ti SUPER; performance acceptance, wider pack/settings coverage and world-transition coverage
remain incomplete.

Reference: https://github.com/avpbynf/Vitrail-Shaders

Inspected upstream: `7a3cdcbe1dfd7e04fb2b3021ee8352d341e7647e`, Vitrail 0.13.0-beta.
Host: Minecraft 26.3, NeoForge 26.3.0.76, Sodium 0.9.3, Java 25.
Branch: `feat/vitrail-vulkan-tv-rework`.

## Rendering path

Replacement level passes register with Vitrail's LevelPass suspend/resume machinery. The optional
viewport bridge supplies the newer host API and restores explicit viewport state after reopening.
The integration resolves the optional ABI once; no Vitrail classes or binaries are bundled.

Packed quads remain 8 bytes. The existing 64-byte model record's final 28 bytes hold six unlit face
colours and a DH material category, populated only for the packed adapter. Material participates in
model deduplication. Vertex pulling reuses the ordinary fluid/surface decoder, normalizes winding
for the pack's culling convention, and supplies camera-relative position, unlit colour, normal,
normalized block/sky light and material. Pack matrices and temporal jitter are applied by the pack.
Shader globals and include macros are isolated from the pack's namespace.

The pack pipeline declares **no vertex binding**. An empty vertex format still creates a slot and
fails current host validation; Vitrail's null-format path omits that slot entirely. Five named SSBO
bindings borrow the existing allocations. Uniform placeholders are 16 bytes while Vitrail supplies
the actual native descriptor range and retains the staging offset. Borrowed facades neither map
nor free their owners, and reject use after owner release.

Opaque LODs run inside Vitrail's distant pass, after ordinary solid terrain and before features.
DH section lists stay empty: the integration skips corner-buffer mapping and submits the existing
GPU-generated indexed indirect batches after the pack binds. Water runs after the pack's deferred
stage. Vitrail owns its colour attachments, distant depth, near-world water seeding and depth
publication. The reversed-Z projection row matches TerraVista's clip planes and publishes render
distance in blocks. The integration advertises the DH shader contract and separates cached packed
translations with `TERRAVISTA_PACKED_LOD` ABI 2.

The distant fragment wrapper compares against the actual main depth image in the matching depth
window, so near terrain can occlude LODs without being published as distant depth. Its sampler is
classified and served through Vitrail's normal sampler path. The depth conversion pair uses bytes
104-111 of the existing scene upload. Upload retirement is deferred until the later water draw;
skipped phases cancel safely, and a missing frame command during a transition waits for GPU idle.
If tree/model uploads rotate the staging bank before either packed pass, its scene parameters are
republished into the current live bank before borrowing the buffer.

Bulk tree and model uploads check their aligned staging budget before consuming queued CPU results.
A pressured tree delta retains its geometry, roots, metadata and cleaner operations together, then
resumes in FIFO order; shutdown frees any retained CPU allocation. Model and blended-colour queues
also keep deferred work. One MiB remains reserved for frame parameters and the later water pass.
The initial 64 MiB bank grows only for a larger atomic batch, up to 256 MiB per bank with at most
four banks. Requests beyond that per-batch bound are rejected explicitly. The ordinary ready path
adds no native event query and reuses its publication callback.

With usable distant programs, TerraVista's vanilla-lit final composite and SSAO are skipped. Its ordinary
opaque prepass still supplies traversal depth. The private temporal draw is omitted because its
depth would not be consumed: both batches are drawn through the pack instead. Reusing the pack's
depth for traversal remains a further optimization to investigate before performance acceptance. Without a drawing pack,
the ordinary renderer remains in use. Packed program adaptation stays off with Distant Horizons
installed; the combined TerraVista + DH rendering case has not been accepted.

After the distant family publishes its program map through its volatile readiness flag, a missing
opaque/water half or a latched prepare refusal selects basic fallback. Warming alone keeps the
packed route. Fallback renders TerraVista's ordinary colour/depth and composites them **after** the
pack's final, while the captured scaled world target is still live. Its depth test preserves the
near world, and upload retirement waits for that later composite; skipped phases cancel safely.
This keeps LODs visible for packs without distant programs, using TerraVista's own lighting/fog.
Those LODs do not enter the pack's deferred lighting, reflections or distant-shadow programs.

## Evidence

- Full Gradle build passed. Dependency restoration required the Windows system proxy and Windows
  certificate trust; direct Java connections had bypassed the configured proxy.
- Pass contract checks against the real upstream JAR cover open/suspend/nesting/close, absent-mod
  identity, failure cleanup and viewport replay. Actual Vulkan clients applied the optional Mixins.
- CPU checks cover linear colour averaging, cutout holes, intrinsic translucency, the 64-byte model
  layout and material-aware deduplication. Shaderc compiled 24 input/type variants, and ordinary
  terrain SPIR-V stayed byte-identical at both tested optimization levels.
- 84 projection cases matched direct matrix projection, upstream near/far recovery and near-world
  occlusion ordering. Complementary's opaque/water vertex and fragment stages compiled through the
  real Mixins. Stage sources can retain multiple conditional mains; every candidate is renamed
  before shaderc chooses the active branch.
- An isolated client passed native buffer handle/range/offset and lifetime checks without adding
  geometry allocations. A client without Vitrail also booted, skipped optional hooks and preserved
  raw pass identity. These startup checks did not open worlds.
- A copied world ran Complementary Unbound r5.9.3 for about a minute, moved the player through four
  locations, exercised a water fixture and exited normally. The current branch run recorded 6,077
  opaque and 6,077 water passes, 18,231 counted indirect calls, and 133,276 non-clear pixels in
  the actual GPU distant-depth image. Mesh queue failures were zero. TerraVista live-buffer
  counters were stable at 27 / 2,367,889,532 bytes in that fixture; these are TerraVista buffer
  counters, not whole-device VRAM measurements.
- The first host runs exercised ordinary indirect fallback. The integration now requests
  `drawIndirectCount` as an **optional** host feature when the packed adapter is enabled. Unsupported
  devices retain the fallback; the draw remains gated by the logical device's actual enabled set.
  The RTX 4070 Ti SUPER then enabled it and recorded 25,404 counted calls with zero fallback calls
  in a complete lifecycle run. This also bypasses fallback count readback and command clearing.
- The current lifecycle run exercised shaders off/on, SDL window resize, 75% rendering at 864x486
  and restoration to 1152x648, followed by a resource reload. Packed opaque/water draws resumed;
  the ordinary renderer handled the shaders-off phase. Real distant-depth pixels remained nonzero
  after resizing and reload, mesh failures were zero, and a probe observed zero private temporal
  draws while packed rendering was active. The run recorded 11,238 counted calls and zero fallback
  calls; TerraVista live-buffer counters returned to 27 / 2,367,889,532 bytes.
  A transient host swapchain-out-of-date exception during resize recovered on the next frame.
- A bounded stress probe forced staging-bank rotation once before opaque and once before water.
  Both draws rasterized, and toggles, resize and resource reload passed again (11,832 counted
  calls, zero fallback calls, 192,984 non-clear distant-depth pixels after reload).
- Backpressure checks cover 20,000 fragmented allocator states against an independent byte-map
  oracle, atomic deferred tree publication/FIFO/cleanup, and model/biome upload budgets at four
  alignments. A real-world budget-boundary probe deferred tree work and later resumed it without
  forced idle or allocation failure. An 80 MiB staging request grew one bank to 128 MiB; invalid
  negative/over-limit requests were rejected. A separate run copied those 80 MiB on the GPU and
  verified distinct markers at the start, middle and end through asynchronous GPU readback.
- Official [Bliss v2.1.2](https://github.com/X0nk/Bliss-Shader/releases/tag/release11), archive SHA-256
  `f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c`, passed packed opaque/water,
  shader toggles, 75% rendering, window resize and resource reload. Repeating after adding fallback
  recorded 11,985 counted draws and 183,102 non-clear distant-depth pixels after reload.
- Official [Bliss v2.0.4](https://github.com/X0nk/Bliss-Shader/releases/tag/Release8) contains no
  `dh_` entry files. Its basic fallback passed toggles, scaling, resize and resource reload with
  zero packed submissions. A no-draw control changed zero pixels; enabling the deferred composite
  changed 100,012 colour pixels at 960x540, 81,273 at 864x486 and 144,549 after reload at 1152x648.
  The probe takes exactly one before/after GPU readback pair for the selected frame. Earlier
  unpaired readback counts included later-frame changes and are not used as this evidence.
- A first paired 1080p Complementary comparison (20 s warmup, 30 s sampled, VSync/limiter off)
  measured Vitrail alone versus TerraVista + Vitrail: mean frame interval 1.70 / 2.99 ms, p95
  3.15 / 4.96 ms; raw hardware GPU render span 1.67 / 2.98 ms. The full run added visible LOD
  geometry (3 MiB used, 256 nodes) and recorded zero mesh failures. Whole-device memory medians
  were 3,756 / 6,447 MiB, including the desktop and the complete TerraVista renderer/preallocation.
  This is one small static-scene pair, not equal-image optimization evidence or overall acceptance.
  Exact scripts, raw timing samples and device memory data are under `performance-01/`.
- The original test-world files retained their recorded hashes. Probes, screenshots and logs live
  outside the repository under `D:/MCMod/项目资料/TerraVista/vitrail-adaptation/`. GPU depth readback
  enabled COPY_SRC only in the probe; it is not a product texture or copy cost.

## Remaining acceptance

- Repeat matched frame-time comparisons with order controls, then measure heavier world streaming
  and peak VRAM. The initial uncontrolled 30 FPS endpoint is superseded by the first uncapped pair;
  neither a single static fixture nor its absolute FPS proves sustained performance acceptance.
- Exercise more supported pack settings, world transitions and sustained travel. The isolated fixture
  disabled NeoForge's early loading window after an upstream OpenGL handoff failure; ordinary launcher behavior still needs checking.
- Measure sustained upload pressure and peak staging/VRAM use in a heavier world. The bounded
  bank policy now defers bulk producers; bypassing that policy with unguarded low-level raw
  allocations can still exhaust the allocator, and a single batch larger than 255 MiB is rejected.
- Validate winding and depth seams in more views, mixed-tint faces, modded materials and water from
  both sides. Face colours are flat LOD approximations; conditional tint currently applies to the
  averaged face as a whole. Unclassified blocks use the unknown material category.
- The basic fallback preserves visibility without a pack-specific LOD lighting contract; verify
  its seams, translucent overlap and temporal effects in more scenes. LOD shadow casting is not
  connected to the packed path. Current evidence proves rendering in the stated fixture, not
  completion of the entire integration goal.

# Vitrail integration branch

Status: in development. Complementary LOD rendering has been exercised in a real Vulkan world;
shader toggles, scaling and resource reloads also passed. Performance acceptance, additional packs
and world-transition coverage remain incomplete.

Reference: https://github.com/avpbynf/Vitrail-Shaders

Inspected upstream: `7a3cdcbe1dfd7e04fb2b3021ee8352d341e7647e`, Vitrail 0.13.0-beta.
Host: Minecraft 26.3, NeoForge 26.3.0.76, Sodium 0.9.3, Java 25.
Branch: `feat/vitrail-shaders`.

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

With an active pack, TerraVista's vanilla-lit final composite and SSAO are skipped. Its ordinary
opaque prepass still supplies traversal depth. The private temporal draw is omitted because its
depth would not be consumed: both batches are drawn through the pack instead. Reusing the pack's
depth for traversal remains a further optimization to investigate before performance acceptance. Without a drawing pack,
the ordinary renderer remains in use. Packed program adaptation stays off with Distant Horizons
installed; the combined TerraVista + DH rendering case has not been accepted.

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
  locations, exercised a water fixture and exited normally. The latest accepted rendering run
  recorded 6,324 opaque and 6,324 water passes, 18,972 native indirect fallback calls, and 133,382
  non-clear pixels in the actual GPU distant-depth image. Mesh queue failures were zero. TerraVista live-buffer
  counters were stable at 27 / 2,367,889,532 bytes in that fixture; these are TerraVista buffer
  counters, not whole-device VRAM measurements.
- The first host runs exercised ordinary indirect fallback. The integration now requests
  `drawIndirectCount` as an **optional** host feature when the packed adapter is enabled. Unsupported
  devices retain the fallback; the draw remains gated by the logical device's actual enabled set.
  The RTX 4070 Ti SUPER then enabled it and recorded 25,404 counted calls with zero fallback calls
  in a complete lifecycle run. This also bypasses fallback count readback and command clearing.
- Two lifecycle runs exercised shaders off/on, SDL window resize, 75% rendering at 864x486 and
  restoration to 1152x648, followed by a resource reload. Packed opaque/water draws resumed; the
  ordinary renderer handled the shaders-off phase. Real distant-depth pixels remained nonzero
  after resizing and reload, mesh failures were zero, and a probe observed zero private temporal
  draws while packed rendering was active. TerraVista live-buffer counters returned to 27 / 2,367,889,532 bytes.
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
- The original test-world files retained their recorded hashes. Probes, screenshots and logs live
  outside the repository under `D:/MCMod/项目资料/TerraVista/vitrail-adaptation/`. GPU depth readback
  enabled COPY_SRC only in the probe; it is not a product texture or copy cost.

## Remaining acceptance

- Measure CPU/GPU frame times, streaming and VRAM in a matched scene; the observed 30 FPS endpoint
  was affected by inactivity limiting and is not a performance result.
- Exercise another supported pack, world transitions and sustained travel. The isolated fixture
  disabled NeoForge's early loading window after an upstream OpenGL handoff failure; ordinary launcher behavior still needs checking.
- Measure sustained upload pressure and peak staging/VRAM use in a heavier world. The bounded
  bank policy now defers bulk producers; bypassing that policy with unguarded low-level raw
  allocations can still exhaust the allocator, and a single batch larger than 255 MiB is rejected.
- Validate winding and depth seams in more views, mixed-tint faces, modded materials and water from
  both sides. Face colours are flat LOD approximations; conditional tint currently applies to the
  averaged face as a whole. Unclassified blocks use the unknown material category.
- Packs without usable distant programs currently cannot draw the packed LOD family. Provide and
  validate a fallback before advertising general shader-pack coverage. LOD shadow casting is not
  connected to this packed path. Current evidence proves rendering in the stated fixture, not
  completion of the entire integration goal.

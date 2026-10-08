# Compositor construction specification

## Status and scope

Implemented and checked 2026-10-08. Batch transparency, masking, blend modes, multipass blur/glow,
catalog interaction, diagnostics and a device profiling workflow on Swift/Kotlin.

## Exemplar workflow

Open Compositor studio; move layer/mask; feather, blend and blur/glow; compare;
change quality; run/stop Profile redraws; background/resume; reset and reopen.

## Source documents and authority

[Compositor contract](../../contracts/behavior/compositor.md),
[preview contract](../../contracts/behavior/graphics-previews.md),
[graphics ownership](../../ARCHITECTURE.md#graphics-integration), and
[notes workflow](../NOTES.md) are established. Transport/persistence are not
applicable. Physical-device GPU cost remains unknown. GLES 2 timing is explicitly
unavailable rather than upgrading the platform/context or introducing JNI.

## Nouns, states, and invariants

AlphaImage owns straight sRGB RGBA8 input. CompositeSettings owns admitted values.
PreviewContent gains one consumer; native surface identity remains stable.
CompositorPipeline/CompositorRenderer own three RGBA8 targets and two uploads.
GraphicsProfile owns observation snapshots, not scheduling or benchmark claims.
CPU/GPU timing and estimated texture payload remain separately labeled.

## Workflow and side-effect ordering

Admit/copy inputs → upload linear premultiplied bytes once → allocate targets on
size change → mask layer → horizontal blur → vertical blur → final composite.
Never sample a texture while writing it. Compile lazily once per context. Native
surface lifecycle gates continuous profiling; callbacks are ignored after removal.
GPU timing reads completion, not submission. No synchronous GPU wait in app frames.

## Required file specifications

Paths below are relative to the platform root unless repository-prefixed.

| Files | Ownership, symbols, dependencies, ordering, refusal and checks |
| --- | --- |
| graphics `CompositorValues.swift` / `CompositorValues.kt` | AlphaImage, CompositeBlend, CompositeSettings, GraphicsProfile and premultiplied transfer helper. Pure CPU values, native upload bytes; bounds/ownership/shared pixel fixtures. |
| graphics `PreviewValues.swift` / `PreviewValues.kt` | Composite PreviewContent case with identity-based image equality. Existing image/mesh API unchanged. |
| graphics `Metal/CompositorPipeline.swift` / `gl/CompositorRenderer.kt` | Lazy bundled program, two source uploads, three targets, pass ordering, profile resource counts; release/replace with owning context and classify allocation capability failure. Native pixel/cache checks. |
| graphics `Shaders/Compositor.metal` / `foundry_graphics/compositor.vert` and `.frag` | Layer mask, separable Gaussian and final linear blend/glow/comparison. Bounded nine taps, top-left parity, opaque final output. Native execution checks. |
| graphics `Metal/PreviewPipeline.swift`, `Metal/MetalPreviewSurface.swift` / `gl/PreviewRenderer.kt`, `gl/GPUPreviewSurface.kt` | Delegate composite preparation/draw without breaking other consumers; optional onProfile callback, completed Metal timing, GLES unavailability, main-thread/disposal gates. Hosted/device lifetime tests. |
| app `Graphics/CompositorStudioView.swift` / `ui/graphics/CompositorStudioScreen.kt` | Catalog-only authored overlay fixture, async base load, bounded feature state, tools/sliders/reset/quality/profile toggle/public failures; no handles. Native interaction tests. |
| app navigation/home and generated iOS project | Register Compositor studio and new files; preserve existing destinations. |
| `contracts/fixtures/graphics/compositor.json`; native `CompositorValuesTests.swift`/`CompositorValuesTest.kt` | Shared bounded settings, linear premultiplied transfer/zero-alpha, owned snapshots. |
| graphics `MetalCompositorTests.swift`; app Android `CompositorStudioTest.kt`; iOS `CompositorLayoutTests.swift` | Actual output and parameter differences, cache counts, target resizing, native controls, lifecycle/profile disposal; no screenshot-only pass. |
| `docs/GRAPHICS-PROFILING.md`; shared composition pattern and profiling technique notes; native graphics/catalog walkthroughs and indexes | Repeatable real-device workload/trace protocol, reasoning, language/runtime changes and actual evidence. |
| root/module READMEs, architecture, contracts index, AGENTS and setup docs | Link implemented scope, controls, dependency and measurement limits. |

## Cross-file dependency map

Catalog → PreviewContent → retained native preview surface → compositor passes.
Admitted CPU inputs → identity uploads. Completion/resource observations → optional
main-thread profile callback. No UI themes, services or product rules in graphics.

## Transport and persistence mapping

Not applicable. One local transient composition and deterministic fixture.

## Verification plan

Pure fixture/ownership tests; real Metal pixels/cache/resource counts; Android
PixelCopy/UI/context/profile checks; iOS hosted bounds/profile/removal; both builds
and regressions. Validate reset and zero-opacity identity, blur/feather edges and
different blends, target recreation and no publication after disposal. Run
notes/assets/diff checks. Record unavailable timing honestly.

## Construction order

Contract → admitted values → multipass shaders/native ownership → catalog → native
pixel/lifetime evidence → profiling protocol and asynchronous learning handoff.

## Open decisions and unknowns

HDR/high-precision intermediates, arbitrary layers/import/export, GPU timer query
adapter, sustained real-device budget and adaptive quality remain separate work.

## Deviation record

The fixture is authored in each catalog's existing PreviewAssets helper rather
than introducing a new asset file; the same disc/ring formula is used natively.
The existing PreviewLayoutTests also hosts CompositorStudioView. Source inspection
keeps Android program construction outside its encoding timer; first texture
uploads remain inside. Resource checks corrected allocation counts from sets to
individual textures. Final review keeps GL OOM/unsupported targets as capability
failures and other GL errors as original diagnostics/public Internal values.

Actual checks: seven graphics value tests per platform, three real Metal render
tests, sixteen iOS app tests, seventeen Android app host tests and thirty-four
Android device tests passed. Three focused compositor device tests passed again
after error-classification refinement. Both builds, notes/assets checks and diff
whitespace checks passed. iOS manual semantic controls and a CLI screenshot were
reviewed; physical-device cost and full assistive-technology coverage remain open.

## Completion criteria

Both catalogs offer complete static editing/profile workflows; real tests execute
passes and prove input/resource behavior. Notes distinguish observed native
execution from a future physical-device performance claim.

# FoundryGraphics walkthrough

FoundryGraphics establishes an optional GPU boundary with pure control policy
and a retained native Metal renderer, independently of UI tokens and services.

## Origin and reading order

Added 2026-10-08 after forms/themes. Read the [GPU contract](../../../../../../contracts/behavior/gpu-effects.md)
and [construction specification](../../../../../../docs/blueprints/gpu-effects.md), then:

1. [Package.swift](../../../../packages/FoundryGraphics/Package.swift) links kernel and copies bundled shader text.
2. [EffectSettings](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/EffectSettings.swift) admits immutable values and owns quality budgets/events.
3. [EffectClock](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/EffectClock.swift) advances bounded active time.
4. [Effects.metal](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Shaders/Effects.metal) generates a fullscreen triangle and evaluates Ripple/Orbit.
5. [EffectPipeline](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/EffectPipeline.swift) compiles once, owns pipeline state and uploads two aligned SIMD4 uniforms.
6. [MetalEffectSurface](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/MetalEffectSurface.swift) bridges native sizing, touches, redraw mode and disposal.
7. [GPUEffectsView](../../../../apps/FoundryCatalog/Sources/Graphics/GPUEffectsView.swift) composes native controls and maps appearance/visibility/scene motion into running.

## Control and frame flow

The feature owns effect, quality, strength, normalized point and pause/preview
selection. Equal settings do not reconfigure the surface. The coordinator keeps
one renderer, and that renderer owns its pipeline, command queue, clock and
counters. It references the coordinator weakly. Deferred main-actor events avoid
state publication during updateUIView and stop after disposal.

Layout sizes the drawable before Metal acquires a frame. Static updates request
one redraw; active updates select MTKView's paced drawing. Each frame uploads
32 bytes, draws one triangle, presents and commits. Ripple is procedural wave
math. Orbit marches a sphere/torus distance field with a bounded loop, derives
normals and evaluates diffuse/specular/rim lighting. Neither uses mesh assets.
Failure presentation projects the kernel Failure publicly; original compiler/
pipeline errors go separately to the injected diagnostic callback.

## Verification and limits

[EffectPolicyTests](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/EffectPolicyTests.swift)
consumes [shared fixtures](../../../../../../contracts/fixtures/graphics/effects.json)
for clamping/nonfinite values, target sizes, budgets and pause/resume/stall time.
[MetalEffectsTests](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/MetalEffectsTests.swift)
compiles the actual bundled shader, submits offscreen Metal work, waits in the
test only, and compares pixels across effect, point, time and quality.
`make graphics-test` passes two Swift policy tests and one Metal execution test
on this Mac; it also runs two Kotlin policy tests. A missing Metal device explicitly
skips the native execution test rather than substituting fake pixels.

[GPUEffectsLayoutTests](../../../../apps/FoundryCatalog/Tests/GPUEffectsLayoutTests.swift)
hosts the actual catalog and a continuously drawing surface on iOS. It checks
native bounds/texture dimensions, automatic-frame reports, pause/resume and
dismantling. The initial offscreen test did not catch a stretched 1×1 hosted
drawable. Sizing was moved into native layout before frame acquisition, with
an explicit proposed size and a screen-derived scale independent of the reduced
drawable. See [MetalKit mechanics](../../../substrate/metalkit-surface-and-shader-lifetime.md).
Final native evidence and manual interactions are recorded in the
[catalog walkthrough](../../apps/FoundryCatalog/README.md#gpu-effects).

No physical-device performance, thermal/battery, exhaustive resize/orientation,
VoiceOver interaction or external-renderer glass capture is established.
Submission rate is not GPU timing or displayed FPS. Import/export, persistence
and richer engine selection remain separate slices.

## Product previews

The 2026-10-08 follow-up adds two concrete consumers of a shared preview surface.
Read [the preview contract](../../../../../../contracts/behavior/graphics-previews.md), then:

1. [PreviewValues](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/PreviewValues.swift) separates owned image/mesh classes from immutable adjustments, viewport and camera values.
2. [PreviewPipeline](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/PreviewPipeline.swift) replaces textures/buffers only on asset identity changes and submits background plus depth-tested geometry.
3. [Previews.metal](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Shaders/Previews.metal) implements top-left aspect-fit comparison, explicit linear-light edits, orbit projection and studio lighting.
4. [MetalPreviewSurface](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/MetalPreviewSurface.swift) retains the renderer, translates incremental native gestures and shares quality/clock/event policies.

Catalog decoding occurs off main, admission refuses malformed/transparent image
data or malformed triangles, then GPU upload occurs on the renderer actor.
Editing values update uniforms; they do not reconstruct the native view or
upload the asset again. SwiftUI owns feature state, UIKit owns gesture detection,
and the renderer owns Metal resources. Turntable defaults off; static edits
still redraw while motion is reduced.

[PreviewValuesTests](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/PreviewValuesTests.swift)
consume the [shared preview fixture](../../../../../../contracts/fixtures/graphics/previews.json)
and check defensive ownership, nonfinite defaults, invalid dimensions/alpha,
mesh normals and material slots. [MetalPreviewsTests](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/MetalPreviewsTests.swift)
executes the bundled shaders on actual textures and the authored lamp mesh.
`make graphics-test` now passes four Swift value/policy checks plus two Metal
execution checks, and four Kotlin host checks. It verifies filtering, top/bottom
orientation, pan/zoom, finish and camera differences; it does not promise matching
pixels across drivers. [PreviewLayoutTests](../../../../apps/FoundryCatalog/Tests/PreviewLayoutTests.swift)
hosts both actual feature views and checks static updates, sizing and cleanup.

The internal triangle JSON is intentionally small. General import, arbitrary
materials, HDR environments, scene graphs, export and durable editing are not
implemented. Read [texture/mesh mechanics](../../../substrate/metal-preview-textures-and-meshes.md)
and [editable values/resource ownership](../../../../../../notes/patterns/editable-values-and-renderer-resources.md).

## Questions and related

- Why does the first resumed clock frame add no elapsed time?
- Why can correct offscreen pixels coexist with an incorrectly sized native canvas?
- Why must uniform alignment agree across Swift and Metal?
- What would be needed to measure GPU time rather than submissions?

Read [shared frame ownership](../../../../../../notes/patterns/renderer-frame-ownership.md)
and [MetalKit mechanisms](../../../substrate/metalkit-surface-and-shader-lifetime.md).

## Expanded GPU use cases

The 2026-10-08 expansion adds Flow, Material, Liquid, Particles and Field to
the existing GPU effects destination. Read the updated EffectSettings and shader
before the catalog controls. Liquid takes supplied completion; Particles takes
a finite playhead; Field takes up to twelve owned point/weight/radius samples.
Field and particle canvases draw on input changes. Only the catalog owns Replay
event timing, with pause/reduction/lifecycle gates and a restart generation.
See [graphics inputs and event time](../../../../../../notes/patterns/graphics-inputs-and-event-time.md) for the cross-platform
reasoning, reusable product examples, upload budget and next questions.

Five graphics value/policy checks and two actual Metal execution tests pass.
The expanded effect test executes all seven branches, tests liquid endpoints,
particle playhead/time independence and changed/empty/zero-weight field data.
All fifteen iOS app regressions pass on iPhone 17 Pro/iOS 26.2 and the app builds.
Manual native checks confirm the seven-option menu, Liquid Empty/Half/Full,
Particles finite completion/reduction, and Field clear/add/trail (0/1/6 samples).
CLI screenshots show Flow, the material card, the half-full gauge and the trail.
An accessibility setValue attempt changed the exposed slider value without
driving SwiftUI state; it is not evidence of a successful iOS scrub interaction.
Actual fixed-playhead pixels are covered by Metal tests, and Android device
tests cover scrub cancellation. VoiceOver and physical-device cost remain open.

## Compositor studio

Claim: a third preview consumer adds transparency and multiple passes without
moving native GPU ownership into feature state.

Origin: the 2026-10-08 batch needed an editor foundation that extends beyond a
single fragment effect. Read [the contract](../../../../../../contracts/behavior/compositor.md), then [admitted values](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/CompositorValues.swift), [pass ownership](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/CompositorPipeline.swift), and [the reused native bridge](../../../../packages/FoundryGraphics/Sources/FoundryGraphics/Metal/MetalPreviewSurface.swift). AlphaImage copies straight sRGB RGBA8 input;
conversion to linear premultiplied bytes happens before upload/filtering. The
four passes retain two source textures and three target textures. Settings changes
reuse both; quality/size changes replace targets; new image identity replaces only
that upload. Switching to image/mesh releases compositor resources.

Metal uses RGBA8 intermediates, an opaque BGRA8 final drawable and command-buffer
completion timestamps. The completion handler reads timing on the completion
thread, captures immutable observations and hops to MainActor before publishing.
The coordinator is weakly captured and its disposed flag gates late delivery.
No production frame waits for GPU completion. Static edits also report profiles.

[Shared CPU checks](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/CompositorValuesTests.swift) consume [compositor fixtures](../../../../../../contracts/fixtures/graphics/compositor.json). [Native pixel checks](../../../../packages/FoundryGraphics/Tests/FoundryGraphicsTests/MetalCompositorTests.swift) exercise actual output rather than merely compiling shaders.
Checks cover bounds/ownership, linear-light blend and orientation, target reuse,
resizing and callback lifetime. Metal additionally checks zero opacity and hidden
RGB at zero alpha through blur/glow. Read the platform index for final run evidence.

The first resource assertion caught counters that counted target sets rather than
textures. Both adapters now report three allocations per size change. Estimates
are payload only; first-frame CPU timing includes different preparation boundaries
on the two platforms. No cross-platform performance claim follows from them.

Read [compositing reasoning](../../../../../../notes/patterns/premultiplied-compositing-and-render-passes.md), [measurement limits](../../../../../../notes/techniques/graphics-profiling-and-measurement.md), and [the device protocol](../../../../../../docs/GRAPHICS-PROFILING.md). Next: which measured cost would justify pass elimination, half-float
intermediates or a GPU timer adapter? General layers, HDR and export remain open.

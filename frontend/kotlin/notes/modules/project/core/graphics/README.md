# Graphics module walkthrough

core:graphics owns an optional native GPU surface and pure frame policies;
the app supplies values and callbacks without owning OpenGL handles.

## Origin and source order

Added 2026-10-08, targeting API 24+ with OpenGL ES 2. Read the
[GPU contract](../../../../../../../contracts/behavior/gpu-effects.md), then:

1. [Build](../../../../../project/core/graphics/build.gradle.kts) links kernel, Compose and lifecycle, with no core/ui dependency.
2. [EffectSettings](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/EffectSettings.kt) defines admitted snapshots, quality budgets and events.
3. [EffectClock](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/EffectClock.kt) retains phase and resets its monotonic baseline.
4. [Vertex](../../../../../project/core/graphics/src/main/assets/foundry_graphics/effects.vert) and [fragment](../../../../../project/core/graphics/src/main/assets/foundry_graphics/effects.frag) shaders implement the procedural effects.
5. [EffectRenderer](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/gl/EffectRenderer.kt) owns every GL handle, source compilation, uniforms and drawing.
6. [GPUEffectSurface/EffectGLView](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/gl/GPUEffectSurface.kt) connects AndroidView, lifecycle, immutable queued updates, native touch and paced redraw requests.
7. [GPUEffectsScreen](../../../../../project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreen.kt) owns controls and consumes token motion plus public events.

## Walkthrough

Settings factories clamp finite values and default nonfinite input. Private
constructors plus init requirements preserve invariants even through data-class
copy. The feature saves control primitives, while GPU resources and submission
counters are transient. No renderer is constructed per recomposition.

AndroidView retains the native view. Callback wrappers read rememberUpdatedState;
queued GL closures capture settings and booleans first. Main owns the scheduler
and lifecycle; GL owns program/uniform handles, frame clock and counters. Frames
upload two vec4 uniforms and draw from one retained direct vertex buffer.
GLSL flips gl_FragCoord.y so top-left touch coordinates match Metal behavior.
Equivalent native output is required; byte-identical cross-driver pixels are not.

The view uses WHEN_DIRTY and Choreographer to pace requestRender. Static control
changes still draw. setFixedSize changes framebuffer dimensions independently of
the view's logical touch area. Pause releases the EGL context; resume triggers
onSurfaceCreated and a fresh shader/program compilation. Removal stops requests,
pauses the view and gates queued publications. Shader/link errors retain the
original exception for diagnostics and return a public internal Failure.

## Verification and limits

[EffectPolicyTest](../../../../../project/core/graphics/src/test/kotlin/dev/mobilefoundry/graphics/EffectPolicyTest.kt)
passes two host tests against [shared fixtures](../../../../../../../contracts/fixtures/graphics/effects.json),
including nonfinite inputs, target bounds and clock pause/stall/resume behavior.
`make android-build` and `make android-test` pass with this module included.

[GPUEffectsScreenTest](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreenTest.kt)
adds three native checks. PixelCopy reads the real Surface, verifies nonblank and
changed output for touch/effect while reduced motion is active, and asserts
Economy's actual target dimensions. A controlled lifecycle confirms that
background pauses publication, resume recreates a ready context and progresses,
and removal prevents later events. Navigation reopening constructs a usable
renderer; native Animate/Reduce motion controls stay functional. All 25 device
checks pass on API36_Test/Android 16, including all 22 existing checks.

These results establish emulator execution, not sustained physical-device frame
time, power/thermal behavior, all GLES driver precision or TalkBack gestures.
Submission counts/rates are not GPU timings. UI backdrop capture cannot sample
this external SurfaceView. General import, scene persistence and a richer
engine remain separate work.

## Product previews

The 2026-10-08 follow-up shares a native preview adapter between image adjustments
and a mesh product configurator. Read the [preview contract](../../../../../../../contracts/behavior/graphics-previews.md), then:

1. [PreviewValues](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/PreviewValues.kt) owns defensive asset copies and bounded edit/camera admission.
2. [PreviewRenderer](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/gl/PreviewRenderer.kt) compiles one program per context, replaces uploads only on asset identity changes and draws a depth-tested VBO.
3. [Vertex](../../../../../project/core/graphics/src/main/assets/foundry_graphics/preview.vert) and [fragment](../../../../../project/core/graphics/src/main/assets/foundry_graphics/preview.frag) shaders define perspective, top-left image comparison, linear-light edits and studio lighting.
4. [GPUPreviewSurface](../../../../../project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/gl/GPUPreviewSurface.kt) translates native drag/pinch, captures immutable GL updates and retains lifecycle scheduling/disposal.

RasterImage/PreviewMesh preserve CPU content across EGL context loss, while
renderer-owned texture/VBO handles are rebuilt on creation. Small edit snapshots
do not copy image or mesh arrays. The app owns editable state and reads it inside
each incremental gesture callback, so several native events before recomposition
accumulate correctly. Quality does not change gesture coordinates. Turntable
defaults off and reduced motion still permits static edits.

[PreviewValuesTest](../../../../../project/core/graphics/src/test/kotlin/dev/mobilefoundry/graphics/PreviewValuesTest.kt)
consumes the [shared fixture](../../../../../../../contracts/fixtures/graphics/previews.json)
and checks defensive ownership/nonfinite defaults/malformed admission. There are
now four Kotlin graphics host checks. The [device checks](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/PreviewStudioTest.kt)
verify image tools, quality targets, mesh finishes/camera/pinch, motion controls,
context resource rebuilding, disposal and catalog reopening using actual pixels.
The first run caught lost deltas between compositions; callback state reads were
corrected. A separate first-swap read returned PixelCopy.ERROR_SOURCE_NO_DATA;
tests now retry that status briefly and still require successful readback.
All 29 device checks pass on API36_Test / Android 16. A final focused run of all
four preview checks also passes after bounding uploaded turntable phase. Host
regressions pass. Physical-device performance and TalkBack gestures remain open.

General mesh import, arbitrary materials, HDR environments, export and durable
editing remain separate capabilities. Read [asset/gesture mechanics](../../../../substrate/gles-preview-assets-and-gestures.md)
and [editable values/resource ownership](../../../../../../../notes/patterns/editable-values-and-renderer-resources.md).

## Questions and related

- Why is preserveEGLContextOnPause=false useful to exercise recreation now?
- Why capture the UI-owned active boolean before queueEvent?
- Why can native framebuffer pixels be absent from a Compose capture?
- Which three quality costs change together, and which remain unmeasured?

Read [GL/Compose mechanics](../../../../substrate/glsurfaceview-and-compose-lifetime.md)
and [shared frame ownership](../../../../../../../notes/patterns/renderer-frame-ownership.md).

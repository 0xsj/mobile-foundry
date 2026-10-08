# GLES preview assets and gestures

Owned CPU assets survive EGL recreation while immutable editing snapshots and
native gestures update a retained renderer without repeatedly uploading data.

## Origin and evidence

Added 2026-10-08 using Kotlin 2.3.20, Compose BOM 2026.03.01 / AndroidView,
GLES 2, API 24 minimum,
and API36_Test / Android 16 execution. Shared host fixtures validate input
admission. Device checks read actual Surface pixels with PixelCopy; ordinary
Compose capture does not establish external-surface output. Final execution
counts and limits are recorded in the graphics walkthrough.

## What and why

RasterImage copies its ByteArray and PreviewMesh copies its FloatArray. Their
classes keep reference identity as the local upload key. Editing values are
data classes with private constructors, bounded factories and init requirements.
@ConsistentCopyVisibility keeps generated copy visibility aligned with the
private constructor; controls use admitted factories instead of unchecked copies.
This avoids an upcoming compiler visibility change without weakening invariants.
Read [data-class mechanics](../language/kotlin-sealed-ui-states-and-data-classes.md#admitted-data-and-copy-visibility)
for language behavior and alternatives.

Bitmap decoding and JSON parsing run withContext(Dispatchers.IO). Catalog
publication is explicitly scoped to Dispatchers.Main.immediate. A focused test
initially observed Compose apply/remeasure on a DefaultDispatcher worker during
asset completion; explicit main publication removed that failure. This is an
observation of this test/runtime combination, not a claim that every IO decode
needs a special dispatcher workaround. CancellationException is always rethrown.

Texture upload uses a direct RGBA byte buffer, GL_LINEAR and CLAMP_TO_EDGE, which
also permits the bundled non-power-of-two image on GLES 2. Mesh upload uses an
interleaved static VBO: 28-byte stride, normal offset 12, slot offset 24. Upload
buffers are allocated on asset changes, not each frame. The context owns handles;
onSurfaceCreated clears old identities and rebuilds from retained CPU input.

## Example

Excerpt from GPUPreviewSurface's native gesture adapter:

```kotlin
// Excerpt
override fun onScale(detector: ScaleGestureDetector): Boolean {
    if (!disposed) gesture(CanvasGesture.Zoom(detector.scaleFactor))
    return true
}
```

ScaleGestureDetector emits incremental factors. Image zoom multiplies by that
factor; camera distance divides by it. Single-pointer drags use view dimensions,
independent of quality's smaller framebuffer. rememberUpdatedState makes native
callbacks see current feature values rather than their initial closure snapshot.
Incremental gestures must also read the latest editable state inside the callback.
A precomputed camera/viewport captured by the last composition loses deltas when
several native events arrive before another composition. The first product drag
pixel check caught this; constructing from the current state primitives preserves
every delta, including the test's burst of synthetic MotionEvents.

## Gotchas

- Attributes/varyings and uniform layouts must match both shaders. Explicit
  mediump varyings/shared uniforms avoid default-precision disagreement on GLES 2
  devices without fragment highp. Turntable phase wraps every full rotation on
  CPU, keeping the uploaded time bounded even after a long active session.
- Native submission statistics can arrive before the first EGL swap is readable.
  PixelCopy tests retry ERROR_SOURCE_NO_DATA briefly, then require SUCCESS;
  other statuses still fail. A statistics event is not presentation completion.
- The pinch test initially swapped argument order and crossed its fingers instead
  of spreading them. Named start0/end0/start1/end1 arguments corrected the test;
  the native detector did not need a workaround.
- Parent scrolling and surface gestures need separate ownership. Disallow parent
  interception during canvas touches; release it on up/cancel.
- Decode returning to main and queueEvent capturing immutable input are different
  boundaries. Neither permits UI mutation from the GL thread.
- Reduced motion suppresses automatic turntable rotation, not static redraws.

## Used in and related

Read the [graphics walkthrough](../modules/project/core/graphics/README.md#product-previews)
and [app walkthrough](../modules/project/app/README.md#product-previews), then
[GLSurfaceView lifetime](glsurfaceview-and-compose-lifetime.md) and the
[shared pattern](../../../../notes/patterns/editable-values-and-renderer-resources.md).
Primary references: [GLUtils](https://developer.android.com/reference/android/opengl/GLUtils),
[OpenGL ES 2 specification](https://registry.khronos.org/OpenGL/specs/es/2.0/es_full_spec_2.0.pdf),
and [glTF material specification](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html).
Gesture test reference: [TouchInjectionScope](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/TouchInjectionScope).
This preview is not a general glTF importer or complete PBR engine.

## Compositor framebuffer passes and timing limits

Observed 2026-10-08 with Kotlin 2.3.20, compile SDK 36 and API36_Test/Android 16;
minimum API stays 24 and the context stays GLES 2. CompositorRenderer lazily links
a fullscreen shader, retains two sources and three RGBA8 targets, and renders
three intermediate passes through a framebuffer object. It checks attachment
completeness, restores the previously bound framebuffer for the final pass and
restores active texture unit zero. Targets are never sampled while attached as
the current output. GLES target sampling flips Y; uploaded top-left CPU pixels
do not. The shaders use highp where available and mediump otherwise.

Admitted straight sRGB RGBA8 converts on CPU before glTexImage2D. Private owned
bytes are exposed only through upload buffers; UI settings cannot mutate pixels.
Control changes do not upload again. Context loss invalidates handles; resume
rebuilds from the retained CPU objects with fresh counters.

CPU encode uses SystemClock.elapsedRealtimeNanos around the GL encoding path;
the first frame includes texture setup/upload, after lazy program creation. There is no GLES 2 GPU query adapter in this slice,
so GraphicsProfile reports null with an explicit reason. Source inspection of
Android's Java bindings is not evidence of a device GPU duration. Publication
posts to main and checks disposal; never mutate Compose state on the GL thread.

Primary reference: [OpenGL ES 2 framebuffer specification](https://registry.khronos.org/OpenGL/specs/es/2.0/es_full_spec_2.0.pdf).
Read [the graphics walkthrough](../modules/project/core/graphics/README.md#compositor-studio)
and [device protocol](../../../../docs/GRAPHICS-PROFILING.md). Driver precision,
GPU counters, physical-device timing and TalkBack gesture coverage remain open.

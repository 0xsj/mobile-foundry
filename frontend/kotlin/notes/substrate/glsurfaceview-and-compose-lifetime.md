# GLSurfaceView and Compose lifetime

Compose owns native-view attachment, while GLSurfaceView's render thread owns
its EGL context and all shader/program handles.

## Origin and versions

Observed 2026-10-08 with Kotlin 2.3.20, Compose BOM 2026.03.01/UI 1.10.6,
compile/target API 36, minimum API 24 and API36_Test/Android 16 emulator.
Primary contracts:
[GLSurfaceView](https://developer.android.com/reference/android/opengl/GLSurfaceView),
[Choreographer](https://developer.android.com/reference/android/view/Choreographer),
[AndroidView](https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/package-summary), and
[PixelCopy](https://developer.android.com/reference/android/view/PixelCopy).

## What and why

AndroidView creates one native view, updates its inputs, and releases it when
removed. rememberUpdatedState lets callbacks held by that view call the latest
composition closures without recreating the view or retaining obsolete state.
DisposableEffect(native, owner) installs and removes a lifecycle observer for
the current view/owner pair. Only RESUMED enables foreground drawing.

GLSurfaceView callbacks run on its GL thread. queueEvent transfers immutable
settings and captured booleans from main to that thread. Merely marking a field
val does not transfer ownership of the object it references; the settings graph
must itself contain immutable values. Native lifecycle and Choreographer access
stay on main. Renderer events post back to main with a disposal check there.

```kotlin
// Conceptual: capture a snapshot before crossing the thread boundary.
val snapshot = settings
val active = foreground && requested
view.queueEvent {
    renderer.settings = snapshot
    renderer.running = active
}
```

Capturing active matters: reading the UI-owned foreground property inside the
queued closure would be a cross-thread read at a different time. The GL thread
must not reach back into a composable or mutable UI owner for each frame.

## Scheduling and context loss

RENDERMODE_WHEN_DIRTY makes requestRender explicit. A main-thread Choreographer
callback paces requests against display callbacks and the quality budget. Pausing
removes that callback; static touch/settings updates still request a render.
The native surface uses setFixedSize to reduce actual framebuffer dimensions
without shrinking its logical touch area.

onPause/onResume are required native lifecycle operations. The exemplar sets
preserveEGLContextOnPause=false: pause releases context resources, and
onSurfaceCreated recompiles its shaders/program after resume. Integer handles
from the lost context are discarded. A direct vertex FloatBuffer is allocated
once; per-frame work uploads uniforms and draws a fullscreen triangle.
ES 2 supports the existing API 24 floor and avoids adding an engine dependency;
AGSL would require a higher API baseline, while a scene engine would introduce
asset/camera/runtime decisions beyond this slice.

## Verification, gotchas and use

A Compose capture does not include a SurfaceView framebuffer. The device tests
use PixelCopy on its native Surface, wait off the UI thread for the copy, then
compare nonblank pixels across touch and effect changes. They also assert the
actual holder dimensions after quality changes. Checking only labels or a
successful shader link would miss a blank/mis-sized surface.

Three graphics device checks pass alongside all 22 previous checks: static
interaction/effect/resolution, lifecycle context recreation/disposal publication,
and navigation removal/reopening with reduced-motion controls. Two host policy
tests pass against shared fixtures. The [module walkthrough](../modules/project/core/graphics/README.md)
links code and tests. Emulator output does not establish physical-device shader
precision, sustained performance or TalkBack gesture behavior. A native external
surface is also outside the existing Compose glass capture source; compare
[backdrop mechanics](compose-backdrop-layers.md) and
[shared frame ownership](../../../../notes/patterns/renderer-frame-ownership.md).

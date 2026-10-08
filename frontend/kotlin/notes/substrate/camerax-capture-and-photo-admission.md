# CameraX capture and photo admission

Claim: CameraX use cases and ImageProxy ownership can end before an admitted
photograph enters an independently owned GPU preview.

## Origin and evidence

Added 2026-10-08 with CameraX 1.6.2, API 36 compilation and Android 16 emulator
execution. The [release page](https://developer.android.com/jetpack/androidx/releases/camera)
lists that stable version. The app pins camera-camera2, camera-lifecycle and
camera-view together in its version catalog.

[CameraX architecture](https://developer.android.com/media/camera/camerax/architecture)
describes lifecycle-bound Preview/ImageCapture use cases. The
[capture callback reference](https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback)
requires the recipient to close ImageProxy and supplies rotation metadata.
Locally observed virtual-camera capture is stronger than compilation, but does
not establish physical lens quality or orientation behavior on real devices.

## What and why

Compose owns explicit permission intent, selected-screen lifetime and the
transient photo. A DisposableEffect starts a controller only when permission,
RESUMED lifecycle and viewfinder state allow it. Disposal unbinds that controller's
Preview/ImageCapture use cases; unbindAll would also disturb unrelated consumers.
CameraState.OPEN enables the shutter rather than assuming bindToLifecycle has
already opened hardware. PreviewView uses its compatible TextureView mode.

The controller runs on main and observes camera state through the current
LifecycleOwner. Capture uses an anonymous `object` implementing the callback
class. It copies bounded JPEG bytes and closes ImageProxy in `finally`, so image
ownership ends even if conversion fails. The actual decode runs in
withContext(Dispatchers.Default), then completion returns to the calling main
scope. CancellationException is rethrown; generation checks reject stale native
callbacks and stale decoded results.

```kotlin
// Conceptual: finish native ownership before suspending for decoding.
val bytes = try { copyCompressedBytes(image) } finally { image.close() }
val photo = withContext(Dispatchers.Default) { decode(bytes, rotation) }
if (request == generation) accept(photo)
```

BitmapFactory reads dimensions before allocation and chooses a sample size
with a maximum 2048 edge. The decoder rotates by CameraX's clockwise metadata,
draws onto an opaque white bitmap and admits RGBA bytes. The requested display
rotation is refreshed at shutter time. The CPU asset then feeds the same
GLSurfaceView editor used by Image studio.

## Gotchas and actual use

Optional camera hardware keeps sample editing available without a lens.
Runtime permission is still required even though the manifest declares CAMERA.
Permission is rechecked on resume after a Settings visit. A grant alone does not
start capture before Enable camera. Removing the camera tab destroys its
composition; shell-owned CPU pixels survive an ordinary tab change, while the
editor and its native surface are recreated. These bytes are not saveable state.

Used in the [app camera walkthrough](../modules/project/app/README.md#camera-and-shared-photo-editor).
Compare [Compose effect lifetime](okhttp-and-compose-effect-lifetime.md),
[GLSurfaceView ownership](glsurfaceview-and-compose-lifetime.md), and
[the shared capture pattern](../../../../notes/patterns/capture-assets-and-preview-lifetime.md).

## Selected-library input — 2026-10-08 follow-up

Choose photo uses Activity 1.13.0's PickVisualMedia(ImageOnly) contract. The
[system-picker guide](https://developer.android.com/training/data-storage/shared/photo-picker)
documents selected-media access and the automatic document-picker fallback.
No READ_MEDIA_IMAGES or full-library permission is added. A null URI means
canceled selection, not a decode failure. Capture is unbound while the picker is
presented or its result is loading; composition-scope cancellation stops obsolete
imports after navigation.

ContentResolver opens the selected URI on Dispatchers.IO. use closes the stream;
an 8 KiB loop checks coroutine cancellation and enforces a 32 MiB encoded limit
before decoding. Dispatchers.Default then performs bounded image conversion.
Opening/reading failures retain the original exception in diagnostics and yield
deliberate recoverable copy. URI access is consumed immediately, not persisted.

AndroidX ExifInterface 1.4.2 is explicitly pinned; see its
[release history](https://developer.android.com/jetpack/androidx/releases/exifinterface)
and [orientation API](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface).
Library decode reads rotationDegrees/isFlipped and applies the horizontal flip
before clockwise rotation. CameraX decode keeps its separate rotation argument,
avoiding double application of camera metadata. Actual format decoding still
depends on the platform's BitmapFactory codecs; unreadable formats return failure.

The final seven-check camera/shell run uses an injected ActivityResultRegistry
for library results, then executes real URI reads, decode, editor and GPU filters.
Other checks exercise canceled selection, malformed bytes, lost URI access,
rotation/transverse dimensions, native virtual capture and navigation. Dimension
checks do not establish reflected pixel placement. The system picker itself
requires separate UI evidence. Initial resource compilation failed to start
AAPT2 daemons; a fresh Gradle process with two workers completed the focused run.
That successful retry does not establish the original daemon failure's cause.

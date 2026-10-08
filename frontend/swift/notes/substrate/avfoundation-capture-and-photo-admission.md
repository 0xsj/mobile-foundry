# AVFoundation capture and photo admission

Claim: queue-confined camera work can hand an upright, bounded CPU photograph
to a main-actor feature without exposing camera ownership to its GPU renderer.

## Origin and evidence

Added 2026-10-08 using Swift 6.2.3, the iOS 26.2 SDK and an iOS 17 deployment
floor. Native source and builds cover the implemented adapters. Hosted decoding
tests exercise ImageIO orientation, bounds and opaque admission. The simulator
uses a sample image; physical camera behavior remains unmeasured.

Apple documents that [startRunning](https://developer.apple.com/documentation/avfoundation/avcapturesession/startrunning())
blocks, so configuration/start/stop run on one serial queue. Its
[RotationCoordinator](https://developer.apple.com/documentation/avfoundation/avcapturedevice/rotationcoordinator)
provides separate preview and capture compensation angles. These documented
APIs inform the adapter; they are not evidence that every device orientation or
interruption has been exercised locally.

## What and why

The main-actor observable controller owns UI state and permission admission.
The engine owns AVCaptureSession, input/output configuration, rotation and one
pending capture. Its serial queue confines mutations and orders start/stop
requests. A UIViewRepresentable hosts AVCaptureVideoPreviewLayer; dismantling
detaches the layer and its rotation observation.

`@unchecked Sendable` is an explicit manual promise, not a compiler proof of
thread safety. Here it permits the engine to cross queue closures while owned
mutable state stays on its queue. `@preconcurrency import AVFoundation` bridges
legacy SDK annotations; it does not make arbitrary concurrent AVFoundation
access safe. The session's documented preview attachment is the narrow exception
to engine-only access. An actor-backed adapter would need the same confinement
for its synchronous native work; making startRunning main-actor isolated would
still block UI work.

The capture delegate associates completion with settings.uniqueID. A generation
on events and photo completion prevents an old attempt from updating a reopened
feature. MainActor/Sendable closure annotations express where and what may cross
the boundary:

```swift
// Conceptual: a native callback still needs current-attempt admission.
Task { @MainActor in
  guard currentGeneration == completedGeneration else { return }
  accept(decodedPhoto)
}
```

Photo decoding asks ImageIO for an orientation-transformed thumbnail with a
maximum 2048 edge. Drawing it into a white sRGB RGBA context flattens transparency
before RasterImage admission. UIKit/CGImage objects do not enter the graphics
package, and no original-size RGBA texture is uploaded first.

## Gotchas and actual use

The simulator branch deliberately offers Sample photo. A generic iOS build
also compiles the physical permission branch, but cannot establish lens access,
front-camera mirroring, interruptions, sensor orientation or thermal behavior.
Stop invalidates pending work and releases running capture; it cannot retract
a delegate callback already queued. Runtime interruption copy offers an explicit
retry rather than a background retry loop.

Used in the [app camera walkthrough](../modules/apps/FoundryCatalog/README.md#camera-and-shared-photo-editor).
Compare [view task lifetime](urlsession-and-view-task-lifetime.md),
[preview textures](metal-preview-textures-and-meshes.md), and
[the shared capture pattern](../../../../notes/patterns/capture-assets-and-preview-lifetime.md).

## PhotosUI input — 2026-10-08 follow-up

Choose photo now replaces the camera's sample action. PhotosPicker presents a
single-image system picker; PhotosPickerItem is a selection handle, not decoded
pixels. Its async loadTransferable(Data.self) loads the chosen representation,
which can involve an iCloud download. The .current encoding preference preserves
the supplied image representation for ImageIO's orientation-aware decoding.
The same PhotoDecoder creates the bounded opaque image used by camera capture.
Canceling the picker supplies no new item. Item/task cancellation and selected-tab
checks reject obsolete completion; opening/importing pauses the native camera.

With this SDK, PhotosPickerItem becomes visible through the PhotosUI/SwiftUI
cross-import overlay. Importing PhotosUI alone in the standalone importer failed
to compile; adding SwiftUI resolved it. The final app suite passes eighteen
checks and the simulator system picker was exercised with a seeded repository
photograph. Cloud downloads and canceled in-flight downloads remain untested.

The encoded-size limit is 32 MiB. Data transfer materializes bytes before that
check, so this is an admission limit, not a guarantee of streaming memory usage.
The app receives no general library authorization, and it does not modify the
selected original. Its imported CPU image remains transient.

See [PhotosPicker](https://developer.apple.com/documentation/photosui/photospicker)
and [loadTransferable](https://developer.apple.com/documentation/photosui/photospickeritem/loadtransferable(type:)).
Actual source use is linked from the app's gallery follow-up.

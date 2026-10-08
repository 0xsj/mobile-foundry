# MetalKit surface and shader lifetime

An MTKView bridge must own sizing and redraw mode independently of SwiftUI's
value updates, while a retained renderer owns compiled Metal state.

## Origin and versions

Observed 2026-10-08 with Xcode 26.2, Swift 6.2.3, iOS 26.2 simulator and an
iOS 17 deployment floor. The graphics package also targets macOS 14 for native
offscreen shader tests. Primary contracts:
[MTKView redraw modes](https://developer.apple.com/documentation/metalkit/mtkview/),
[explicit drawable sizing](https://developer.apple.com/documentation/metalkit/mtkview/autoresizedrawable),
[Metal libraries](https://developer.apple.com/documentation/metal/metal-libraries), and
[representable sizing](https://developer.apple.com/documentation/swiftui/uiviewrepresentable/sizethatfits(_:uiview:context:)).

## What and why

UIViewRepresentable's make/update/dismantle calls bridge a native object's
lifetime. SwiftUI recreates its value description freely; the coordinator retains
the renderer across updates. The renderer's reference back is weak, preventing
a coordinator/renderer retain cycle. MainActor isolation keeps view, settings
and delegate access on one executor; Sendable settings/events contain only values.

Continuous drawing uses isPaused=false and enableSetNeedsDisplay=false with a
preferred frame rate. Static drawing uses isPaused=true and
enableSetNeedsDisplay=true; changed controls request a redraw. Scene phase and
visibility are inputs from the app. Dismantle pauses, removes the delegate,
releases drawables and drops the renderer. Submitted Metal commands retain the
resources they need until completion.

autoResizeDrawable=false lets a quality policy select fewer pixels than native
resolution. Compute the target from native bounds × the window's screen nativeScale and set
drawableSize. A representable must also accept the proposed canvas size instead
of deriving its ideal size from its drawable. The first simulator check rendered
a stretched 1×1 color despite correct shaders and a large visible canvas. An
explicit sizeThatFits/geometry frame alone did not fix automatic frames: resizing
had to move into layout, before frame acquisition. Also use screen scale
independently of the reduced drawable; a later assertion caught the view's
contentScaleFactor changing the next target. The hosted catalog test now checks
actual automatic-frame texture dimensions, pause/resume and dismantling.

## Shader and Swift mechanics

The package copies shader text into Bundle.module and calls makeLibrary(source:)
once at attachment, then creates a retained pipeline. SwiftPM .copy preserves the
Shaders directory instead of invoking a platform-specific shader build step.
Build-time .metallib compilation would avoid runtime source compilation; it is a
separate tooling refinement. Compilation does not occur during frames.

```swift
// Excerpt: CPU layout mirrors two float4 values in the Metal shader.
private struct Uniforms {
    var viewport: SIMD4<Float>
    var interaction: SIMD4<Float>
}
```

SIMD4<Float> is a four-lane value with 16-byte alignment. Two consecutive fields
make the current 32-byte block. MemoryLayout.stride describes the bytes uploaded
with setFragmentBytes. Shader constant-buffer layout must agree; replacing a
float4 with a packed triple without checking alignment can corrupt later values.
The vertex_id attribute generates a covering triangle without a vertex buffer;
the fragment function runs the procedural math per target pixel. Orbit uses a
signed-distance field and finite-difference normals rather than mesh assets.

## Gotchas, use and limits

Do not change SwiftUI state synchronously from updateUIView. Event delivery is
deferred to a main-actor task and ignored after coordinator disposal. Unexpected
initialization errors reach diagnostics separately from public Failure copy.
No CPU waitUntilCompleted belongs in the interactive frame loop; that wait is
used only by the offscreen test before reading pixels.

See the [graphics walkthrough](../modules/packages/FoundryGraphics/README.md)
for source/test links. Native Metal pixel tests pass; simulator layout and controls
are exercised separately. VoiceOver gestures, sustained physical-device frame
time, memory and thermal behavior remain unmeasured. Compare
[shared frame ownership](../../../../notes/patterns/renderer-frame-ownership.md).

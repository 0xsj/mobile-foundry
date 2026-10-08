# Metal preview textures and meshes

Explicit texture encoding, vertex packing and projection conventions let one
retained Metal adapter draw both a filtered image and depth-tested geometry.

## Origin and evidence

Added 2026-10-08 with Swift 6 / Xcode 26.2, iOS 17 minimum and iOS 26.2 simulator
execution. The actual Previews.metal source compiled and rendered on this Mac;
readback checks red-above-blue orientation, filtering, viewport, finishes and
camera differences. iOS hosted tests check both catalog canvases, static redraw,
quality sizing and delegate release. Device thermal/frame-time evidence is open.

## What and why

Raster admission takes opaque, top-left sRGB RGBA8. The catalog uses ImageIO and
an sRGB CGContext off main. The texture is rgba8Unorm, so the shader explicitly
decodes sRGB before exposure/saturation/vignette and encodes afterward. Choosing
an sRGB texture instead would change sampling's conversion and require changing
this shader policy. Transparent and wide-gamut editing are outside this contract.

Mesh vertices are seven contiguous Float values. The vertex function indexes a
device float pointer at vertex_id × 7, avoiding a Swift struct whose padding
could disagree with the asset. Five SIMD4<Float> uniforms occupy 80 bytes with
16-byte alignment. The mesh pipeline uses depth32Float and a less comparison;
the fullscreen background disables depth writes. Metal's projection maps depth
to 0...1, while the Android adapter maps to -1...1 before the viewport transform.

## Example

Excerpt from the linked PreviewPipeline source:

```swift
// Excerpt
case .image(let next, _, _, _):
    guard image !== next else { return }
```

RasterImage is a final class with immutable members and owned Data, allowing
Sendable transfer out of Task.detached without exposing mutable storage. `!==`
compares object identity. Pure edit structs use value equality; they do not force
texture replacement. SwiftUI @State properties each need their own declaration
when a property wrapper is used; combining several variables was rejected by
the initial catalog compile and corrected before runtime verification.
Read [immutable references and identity](../language/swift-result-and-failure-values.md#immutable-references-and-asset-identity)
for the language distinction.

## Gotchas

- Upload only on an asset change; keep assets and editing values out of draw-time
  file access. Task cancellation suppresses publication; it does not interrupt a
  synchronous ImageIO decode already in progress.
- A single-pixel placeholder satisfies the fragment texture binding even when
  the product branch does not sample it.
- Size MTKView before frame acquisition and use a screen-derived scale. Reuse
  the existing layout correction rather than sizing from inside draw.
- An analytic screen-space contact shadow is illustrative. GGX specular alone
  does not make this a complete glTF/PBR renderer or an environment-lighting engine.

## Used in and related

Follow the [graphics walkthrough](../modules/packages/FoundryGraphics/README.md#product-previews)
and [catalog walkthrough](../modules/apps/FoundryCatalog/README.md#product-previews).
Read [MetalKit lifetime](metalkit-surface-and-shader-lifetime.md) and the
[shared ownership pattern](../../../../notes/patterns/editable-values-and-renderer-resources.md).

Primary references: [Metal texture sRGB option](https://developer.apple.com/documentation/metalkit/mtktextureloader/option/srgb),
[Model I/O](https://developer.apple.com/documentation/modelio), and
[glTF material specification](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html).
Model I/O is a future import option; this implementation reads the internal JSON.

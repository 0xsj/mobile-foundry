# FoundryGraphics

Optional native GPU effects and preview module: bounded settings, resolution/time
policies, Metal shaders and iOS native surfaces. Depends only on FoundryKernel and
native frameworks. The UI catalog owns controls and theme consumption.

Ripple renders an interactive wave field; Orbit ray-marches a lit sphere and
rotating torus. These procedural examples require no external assets.

`MetalPreviewSurface` also accepts owned opaque `RasterImage` or `PreviewMesh`
inputs and bounded adjustment/viewport/camera/finish values. Open **Image studio**
for before/after image edits and **Product studio** for mesh orbit/zoom/finishes.
Control changes reuse uploaded assets; rendering stays independent of UI tokens
and product services. The internal triangle format is not a general importer.
Read the [preview contract](../../../../contracts/behavior/graphics-previews.md)
and [texture/mesh notes](../../notes/substrate/metal-preview-textures-and-meshes.md).

Run `make graphics-test` from the repository root for shared policy fixtures and
actual offscreen Metal pixels. `make ios-test` includes the hosted canvas sizing
and disposal regression. Open **GPU effects** in the iOS catalog to interact.

Read the [contract](../../../../contracts/behavior/gpu-effects.md),
[walkthrough](../../notes/modules/packages/FoundryGraphics/README.md), and
[Metal mechanics](../../notes/substrate/metalkit-surface-and-shader-lifetime.md).
Submission counters are not GPU timings; physical-device profiling remains open.

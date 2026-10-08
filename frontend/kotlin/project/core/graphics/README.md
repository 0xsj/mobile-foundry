# core:graphics

Optional Android graphics library: bounded settings, resolution/time policies,
OpenGL ES 2 renderers and Compose native-surface bridges. Depends on kernel,
Compose and lifecycle; it does not depend on UI themes, services or an engine.

Ripple renders a wave field; Orbit ray-marches a lit sphere and rotating torus.
The native canvas owns its context and frame scheduler. The catalog supplies
immutable snapshots and native controls.

`GPUPreviewSurface` also accepts owned opaque `RasterImage` or `PreviewMesh`
inputs and bounded adjustment/viewport/camera/finish values. **Image studio**
and **Product studio** supply two concrete consumers. Editing reuses uploads;
EGL recreation rebuilds from retained CPU data. Product-specific paths and rules
stay in the app. Read the [preview contract](../../../../../contracts/behavior/graphics-previews.md)
and [asset/gesture notes](../../../notes/substrate/gles-preview-assets-and-gestures.md).

Run `make graphics-test` from the repository root for shared policy fixtures.
`make android-ui-test` requires a booted device/emulator and checks actual surface
pixels, interaction, resolution, context recreation and disposal. Open **GPU
effects** in the catalog. Submission counts are not GPU timings.

Read the [contract](../../../../../contracts/behavior/gpu-effects.md),
[walkthrough](../../../notes/modules/project/core/graphics/README.md), and
[GL/Compose mechanics](../../../notes/substrate/glsurfaceview-and-compose-lifetime.md).
Physical-device profiling and external-surface glass capture remain open.

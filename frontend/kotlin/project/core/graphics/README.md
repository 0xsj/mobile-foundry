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

The **GPU effects** gallery also includes Flow (ambient), Material (reflective
card), Liquid (supplied progress), Particles (finite replay/scrub), and Field
(supplied density samples). EffectSettings admits progress and up to twelve
owned EffectFieldSample values. Product meaning and Replay timing stay in the
catalog; the graphics module owns drawing and GPU resources.

**Compositor studio** adds AlphaImage and CompositeSettings to PreviewContent.
Straight sRGB RGBA uploads convert to linear premultiplied pixels before masking,
separable blur and final Normal/Multiply/Screen blend/glow. Three offscreen targets
reuse their dimensions; edits reuse source uploads. The optional onProfile callback
reports payload estimates and CPU encoding, plus completed Metal GPU intervals
when available; GLES 2 explicitly has no GPU timing adapter. See the
[contract](../../../../../contracts/behavior/compositor.md) and
[profiling protocol](../../../../../docs/GRAPHICS-PROFILING.md).

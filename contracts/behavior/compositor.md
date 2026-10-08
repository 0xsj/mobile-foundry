# Native compositor studio

Status: Implemented and checked 2026-10-08. Extends the existing preview adapter; image and
mesh consumers keep their current contracts. No engine or new runtime dependency.

## Inputs and ownership

PreviewContent.composite takes an opaque RasterImage base, an owned AlphaImage
overlay and immutable CompositeSettings. AlphaImage accepts top-left straight
sRGB RGBA8, dimensions 1...4096 and exact byte length. It copies caller bytes;
hidden RGB at zero alpha cannot leak into filtering. Upload converts both input
images to linear premultiplied RGBA8 before bilinear sampling or blur. This is
an SDR 8-bit intermediate exemplar, not HDR/wide-gamut or lossless export.

Settings admit opacity [0,1] default .85, layer center [0,1] default .5/.5,
layer scale [.15,1] default .65 (height relative to canvas), mask center [0,1]
default .5/.5, mask radius [.05,.75] default .32 in canvas-height units, feather
[0,.3] default .12, blur radius [0,24] target pixels default 0, glow [0,1]
default .35, comparison [0,1] default .5, mask enabled default true and blend
Normal/Multiply/Screen. Nonfinite values use defaults; finite values clamp.
Comparison 1 is original and 0 is composed. The base uses aspect-fit; the
overlay has independent placement. A disabled mask admits the whole overlay.

## Rendering and lifetime

One layer/mask pass writes linear premultiplied pixels, horizontal and vertical
nine-tap Gaussian passes blur that target, and a final pass composites over the
base, adds optional blurred glow, clamps SDR and encodes sRGB. Normal uses source
over, Multiply multiplies the base with source color, and Screen uses inverse
multiplication, all in linear space. Blur zero selects the sharp layer for the
blend. Glow uses a minimum 8-pixel blur spread; opacity zero removes both layer
and glow. Transparent black has no influence. Masking precedes blur/glow.

Three RGBA8 targets are retained at the existing quality resolution and replaced
only on size changes. Images upload only when reference identity changes.
Removal releases resources; EGL recreation rebuilds from admitted CPU inputs.
Static edits redraw once. Optional Profile redraws continuously submits identical
content at the selected budget, only while visible/foreground and motion-enabled.
Gesture tools select layer placement, mask placement or comparison; pinch scales
the layer. Native sliders/buttons provide alternatives. Reset restores defaults.

## Diagnostics and measurement

Expected asset/capability failures use kernel values; unexpected shader/resource
defects retain original diagnostic identity and public internal projection.
An optional onProfile callback reports immutable GraphicsProfile snapshots for
the compositor: frame ID, target size, four passes, input texture payload bytes,
three offscreen target payload bytes, upload/reallocation counts and CPU encode
duration. Byte counts exclude driver overhead, depth/display buffers and other
application resources; they are not measured VRAM usage.

Metal reports GPU command-buffer duration only after successful completion and
positive GPU timestamps. Unsupported/zero timestamps are unavailable, never a
CPU timer labeled as GPU time. The GLES 2 adapter explicitly reports GPU timing
unavailable; external device profiling is the measurement path. Neither duration
is displayed FPS, presentation latency, a cross-platform benchmark or thermal
evidence. Continuous profile publication is throttled to once per second; static
edits publish their own completion. Disposed surfaces publish no new callbacks.

## Catalog and evidence

Compositor studio loads the existing photograph and a deterministic authored
transparent color-disc fixture. No import/export, persistence, real backend,
camera/microphone input or arbitrary layer stack is introduced. The example
exercises real transparency, feathering, blend selection, blur/glow, comparison,
quality, native alternatives, reset and lifecycle.

Shared fixtures check admission and transfer/premultiplication. Metal and Android
pixel tests check real output, blend/mask/blur controls, comparison and
input/target retention. Metal additionally checks hidden RGB and opacity zero
through blur/glow. Native layout/context/disposal checks cover
the reused bridge and profiling callbacks. A repeatable device protocol records
warm-up, sustained samples, quality, dimensions, device/build, thermal state and
external traces without inventing physical-device results.

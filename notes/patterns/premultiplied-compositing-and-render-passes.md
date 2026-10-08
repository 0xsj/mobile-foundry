# Premultiplied compositing and render passes

Claim: filter linear premultiplied pixels, and give every pass a distinct output
whose lifetime belongs to the renderer rather than the controls.

## Origin and evidence

The 2026-10-08 compositor slice needed transparent overlays, a feathered mask,
three blend modes and blur/glow on both native adapters. Shared fixtures establish
the CPU transfer and admission rules; actual Metal readback checks establish
linear blending, orientation, transparency and pass output. Native platform
walkthroughs record device and lifecycle evidence separately.

## What and why

A transparent pixel can contain bright RGB that should contribute nothing.
Filtering straight RGBA averages that hidden color with visible neighbors before
alpha is applied, creating fringes. Multiplying **linear** RGB by alpha before
filtering makes every component describe its actual contribution. Mask coverage
and opacity then multiply all four components together. Blur operates on those
same contributions without unpremultiplication near zero alpha.

sRGB bytes encode display brightness rather than linear light. Decode first,
premultiply second, interpolate/composite third, and encode the final opaque SDR
result once. This exemplar stores linear values in eight-bit targets for GLES 2
parity; precision loss in dark/low-alpha values is a deliberate limitation.
Higher-precision/HDR output requires a separate format/capability contract.

Each pass reads completed prior outputs: layer/mask → horizontal blur → vertical
blur → final blend/glow. A texture must never be sampled while it is the active
output. Explicit ownership also makes the memory budget inspectable: two source
textures plus three retained target textures, replaced only on size changes.

## Example

For straight encoded RGBA `[255,128,0,128]`, linear premultiplication gives
`[128,28,0,128]` after rounding. `[255,20,200,0]` becomes `[0,0,0,0]`.
With base linear color B, premultiplied source S and source alpha a:

| Blend | Opaque result before glow |
| --- | --- |
| Normal | B × (1 − a) + S |
| Multiply | B × (1 − a) + B × S |
| Screen | B + S × (1 − B) |

This table assumes an opaque base. A general transparent layer stack needs its
own output-alpha math and cannot reuse these equations indiscriminately.

## Gotchas

- A byte buffer's row order and a render target's sampling origin are separate.
  GLES targets need a Y flip when sampled; CPU uploads and Metal textures use
  the admitted top-left convention in this implementation.
- Mask first, blur second permits soft color/glow outside the mask edge. Masking
  after blur produces a different effect. Product intent must choose the order.
- Blur zero selects the sharp layer; optional glow still reads a blurred target.
  Zero opacity must remove both contributions.
- An allocation counter must say whether it counts individual textures or sets.
  Resource tests caught that ambiguity here; the public count is textures.
- An edited value changes uniforms, not source identity. Replacing an image
  object deliberately triggers a new upload, even with equal bytes.

## Used in and related

Used by Compositor studio for sticker/artwork editors, product decals, masked
reveals and composited brand visuals. Features supply admitted assets/settings;
they own selection, saved recipes and product meaning. Graphics owns passes.

Read [editable values and resources](editable-values-and-renderer-resources.md),
[frame ownership](renderer-frame-ownership.md), and
[honest graphics measurement](../techniques/graphics-profiling-and-measurement.md).

## Next questions

- When would half-float targets justify a separate capability path?
- Which second layer workflow would justify a general pass graph?
- How should imported premultiplied images declare their transfer/alpha format?

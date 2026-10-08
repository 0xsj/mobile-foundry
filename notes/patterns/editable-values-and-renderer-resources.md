# Editable values and renderer resources

An editable preview travels as owned CPU data plus small value snapshots;
textures, buffers and shader programs belong to the renderer's current context.

## Origin and evidence

Added 2026-10-08 while implementing image adjustments and a mesh product viewer.
Source inspection shows resource replacement guarded by asset reference identity.
Native Metal pixel execution verifies filter, camera and finish differences;
native hosting verifies sizing and removal. These checks establish the preview
boundary, not a production editor, asset pipeline or device performance budget.

## What and why

Keep four lifetimes separate:

| Lifetime | Owner | Example |
| --- | --- | --- |
| Decoded asset | Feature or asset cache | Owned opaque pixels or validated triangle data |
| Editable values | Feature | Exposure, pan, camera distance, finish selection |
| GPU resources | Renderer/context | Texture, vertex buffer, pipeline state |
| Durable recipe/reference | Future repository | Asset version and confirmed edits |

A slider creates a new small snapshot referencing the same decoded asset. The
renderer updates uniforms while keeping the existing texture or vertex buffer.
Losing the native context discards its handles; the CPU asset is sufficient to
rebuild them. A new decoded object deliberately signals a new upload. Reference
identity is a local cache key, not a persistent asset identifier or content hash.

## Example: reuse across verticals

Conceptual extension map:

| Foundation | Vertical | Feature work added above the renderer |
| --- | --- | --- |
| Image preview | Marketplace listings | Import, crop, export and upload confirmation |
| Image preview | Journaling or fitness | Private media selection and saved edit recipes |
| Mesh preview | Commerce | Model/variant mapping and availability rules |
| Mesh preview | Interior design | Scene placement, scale and durable transforms |
| Mesh preview | Education | Selection, annotations and lesson state |

The native surface need not know a listing ID or a lesson's navigation. A richer
engine adapter can accept feature values later, when import, scene or AR needs
justify it. The current tiny triangle format and three finishes are concrete
examples, not a universal material/scene schema.

## Gotchas

- Decode and validate away from the UI thread. Publish editable state on main;
  upload resources only on the renderer's native thread/actor.
- Coordinate origin and color encoding are input contracts. A correct filter
  can still show an inverted image or apply exposure in the wrong color space.
- Waiting for GPU completion belongs in a pixel test, not the interactive loop.
- Render on changes for a static editor. Optional turntable motion needs the
  same foreground/reduced-motion gates as other automatic animation.
- Preview/export are different workloads. An on-screen low-resolution result
  does not establish full-resolution output, metadata preservation or HDR.
- Keep unexpected diagnostics separate from public failure copy.

## Used in and related

Read the [Swift notebook](../../frontend/swift/notes/README.md) or
[Kotlin notebook](../../frontend/kotlin/notes/README.md), then their graphics and
catalog walkthroughs for source links and native evidence.
See [frame ownership](renderer-frame-ownership.md) and
[service boundaries](transport-service-and-screen.md) for the neighboring seams.

Next questions: When should decoded assets be evicted? Which edit should be
durable? What versioned material/import contract would justify a new adapter?

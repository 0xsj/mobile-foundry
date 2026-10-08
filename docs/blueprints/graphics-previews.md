# Product graphics previews construction specification

## Status and scope

Implemented specification, 2026-10-08. Implements both user-selected foundations:
image adjustment preview and 3D product configurator, on Swift and Kotlin.

## Exemplar workflow

Open Image studio, compare a bundled photograph, edit exposure/saturation/vignette,
zoom/pan/reset. Open Product studio, inspect a mesh lamp, orbit/zoom, change finish,
optionally turntable, reduce motion, leave and reopen. Asset values are injected;
catalog controls are examples. No backend, export, storage or universal engine.

## Source documents and authority

Established: [architecture](../../ARCHITECTURE.md#graphics-integration),
[organization](../ORGANIZATION.md), [frame contract](../../contracts/behavior/gpu-effects.md),
and [preview contract](../../contracts/behavior/graphics-previews.md). Native
Metal/GL adapters and existing quality/time policies are retained. Unknown:
physical-device budgets and future engine/import/export selection. Transport and
persistence are not applicable to this first preview increment.

## Nouns, states, and invariants

RasterImage/PreviewMesh own admitted CPU asset data and stable instance identity.
ImageAdjustments/ImageViewport/OrbitCamera are pure bounded values. ProductFinish
owns rendering parameters, not commercial variants. PreviewContent identifies
an image or mesh render input. CanvasGesture carries normalized incremental drag
and pinch scale. EffectEvent retains existing ready/submission/failure semantics.
GPU handles never enter these values. Catalog loading is ready or expected failure.

## Workflow and side-effect ordering

Load/decode bundled asset → admit owned data → construct bounded preview values →
attach surface → compile shaders and upload asset → size target before acquisition
→ redraw on input changes → publish event on main. Product turntable alone requests
continuous frames, gated by foreground/motion. Context loss invalidates resources
and rebuilds from CPU values. Removal stops callbacks and releases native handles.

## Expected file tree and file specifications

All rows are Required; paths are relative to the platform root unless stated.
Swift graphics source root is packages/FoundryGraphics/Sources/FoundryGraphics;
Kotlin root is project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics.

| Files | Ownership, important symbols, behavior and checks |
| --- | --- |
| graphics `PreviewValues.swift` / `PreviewValues.kt` | RasterImage, PreviewMesh, ImageAdjustments, ImageViewport, OrbitCamera, ProductFinish, PreviewContent, CanvasGesture. Own immutable admission/gesture transformations; kernel only. Refuse malformed assets before upload. Shared fixtures. |
| graphics `Metal/PreviewPipeline.swift` / `gl/PreviewRenderer.kt` | Native shader compilation, retained image texture/mesh buffers, camera uniforms, depth-tested submission and contextual recreation. Upload on identity change, original diagnostic + public failure. Pixel tests. |
| graphics `Shaders/Previews.metal` / assets `foundry_graphics/preview.vert`, `preview.frag` | Aspect-fit image/filter/comparison and mesh lighting/background functions. Explicit uniform layout, sRGB and top-left policy. Real native pixels. |
| graphics `Metal/MetalPreviewSurface.swift` / `gl/GPUPreviewSurface.kt` | Shared native surface for the two preview inputs; native drag/pinch callbacks, layout, static/turntable scheduling, lifecycle and disposal. Reuse existing quality/clock/events. Hosted/device checks. |
| app `Sources/Graphics/PreviewAssets.swift` / `ui/graphics/PreviewAssets.kt` | Decode bundled image/mesh off main, admit inputs, return expected asset failure. Catalog-only paths, no network/provider logic. |
| app `Sources/Graphics/ImageStudioView.swift` / `ui/graphics/ImageStudioScreen.kt` | Image feature controls/values, tools, static canvas and public loading/error handling. No GPU handles. Native input/pixel tests. |
| app `Sources/Graphics/ProductStudioView.swift` / `ui/graphics/ProductStudioScreen.kt` | Product feature finish/camera/turntable/reduction controls; lifecycle gating and reset. No commercial variant assumptions. Native input/pixel tests. |
| graphics tests `PreviewValuesTests.swift`, `PreviewPixelsTests.swift` / `PreviewValuesTest.kt` | Canonical fixture admission; actual Metal image/mesh output changes and depth. |
| app `Tests/PreviewCanvasTests.swift` / device `ui/graphics/PreviewStudioTest.kt` | Hosted dimensions/static updates/disposal; actual GL pixels/tools/finish/camera/lifecycle/navigation. |
| root assets `source/studio-still-life.png`, `source/studio-lamp.json`, `manifests/studio-previews.json` | Canonical generated photograph, repository-authored mesh, provenance and hashes. App resources/graphics assets are synchronized copies. |
| root `scripts/sync_graphics_assets.py` | Deterministic mesh creation, asset copy/check; no runtime dependency. A --check verifies packaged copies against canonical content. |
| mirrored graphics/app notes, shared pattern and native substrate notes | Explain CPU values vs GPU lifetime, texture coordinates/color and mesh/camera mechanics with actual evidence. |

Existing navigation/home, Swift project resources, Makefile, graphics READMEs,
contracts/README, architecture/organization/setup and reading indexes are updated.
Required fixture: contracts/fixtures/graphics/previews.json. A build module per
example and application-data abstractions inside graphics are Forbidden. General
mesh import, export, persistence, camera capture and provider services are Deferred.

## Cross-file dependency map

Catalog asset decoding → admitted CPU values → preview surface → native pipeline.
Catalog gestures → pure values → surface snapshot. UI tokens stay in catalog.
The new preview surface is shared by the two concrete consumers; procedural
effects stay independently usable. No third-party engine dependency is introduced.

## Transport and persistence mapping

Not applicable. Editable CPU values can later be persisted by features; no local
durability, export, provider sync or remote asset loading is claimed here.

## Verification plan

Run graphics tests including actual Metal pixels, both app builds, iOS hosted
checks and Android native pixel/interaction/lifecycle checks. Inspect screenshots
of both examples. Run assets-check, notes-check and diff checks. Compare frame
submissions honestly; physical-device profiling remains open.

## Construction order

Contract/fixture → values/assets → native pipelines → lifecycle/gesture surfaces →
catalog composition → native verification → learning handoff/deviation record.

## Open decisions and unknowns

Production image import/export and color targets, glTF/RealityKit/Filament
adapters, model compression/caching, device budgets and durable scene editing.

## Deviation record

- Tests are named `MetalPreviewsTests.swift` and `PreviewLayoutTests.swift`,
  rather than PreviewPixels/PreviewCanvas in the initial tree. Ownership and
  intended native execution coverage are unchanged.
- Raster admission explicitly requires opaque alpha 255; transparent compositing
  remains deferred. Normals are bounded per component and checked for nonzero
  length before upload. Shared fixtures exercise these limits.
- Native rendering stays on the existing Metal/GLES substrate. The authored
  triangle JSON avoids committing to a general importer/engine for one asset.
  GGX specular, ambient fill and an analytic shadow are illustrative studio
  lighting, not a claim of full glTF/PBR compliance.
- Initial checks caught Swift catalog wrapper/token wiring errors, Kotlin range
  syntax and Swift test type inference; these were corrected before execution.
  Native Android tests caught incremental deltas applied to a captured camera,
  first-buffer PixelCopy timing, and a test-only pinch argument-order mistake.
  The notes record each correction without substituting build success for pixels.
- Final checks: four value/policy checks per native platform, two real Metal
  execution checks, all 15 iOS app checks, Android host regressions, all 29
  Android device checks plus the final four-preview focused run. Both apps build;
  assets/notes/diff checks pass. Physical-device profiling remains open.

## Completion criteria

Both catalogs provide actual editable image and mesh previews, retain resources
across control updates, handle removal/context recreation, and provide portable
values and learning notes suitable for other verticals. Verification must inspect
real pixels and preserve the existing procedural gallery.

# Product graphics previews

Status: Implemented native preview scope, 2026-10-08.

## Reusable boundary

FoundryGraphics/core:graphics accepts an immutable RasterImage or PreviewMesh,
bounded editing/camera values, quality and requested animation. Native adapters
own textures, buffers, programs, frame scheduling and context recreation.
Catalogs own asset selection, controls, editable values and lifecycle inputs.
No product IDs, services, UI tokens or commerce rules enter the graphics library.
The same inputs can support media editors, listings, personalization, design tools
and product inspection. Platform output must be equivalent, not byte-identical.

## Image preview

RasterImage admits owned opaque top-left sRGB RGBA8 pixels, positive dimensions at most 4096,
exactly width × height × 4 bytes and alpha 255. Transparent inputs are refused
until a compositing policy is added. Admission refuses invalid dimensions/data;
later caller mutation cannot alter admitted pixels. Each instance has stable
identity; changing controls does not re-upload the texture.

ImageAdjustments owns exposure in [-2,2] EV, saturation in [0,2] and vignette in
[0,1]. Nonfinite values default to 0, 1 and 0. ImageViewport owns zoom [1,4] and
normalized pan [-0.75,0.75], defaulting to 1/0/0. Comparison is a screen-space
divider in [0,1], default 0.5. Left is original, right is adjusted. Both halves
share aspect-fit, zoom and pan. Outside-image pixels show a neutral background.
The filter decodes sRGB to linear, applies exposure then luminance-based saturation
then a source-space radial vignette, clamps and encodes sRGB. It does not claim
RAW, HDR, wide-gamut editing or production color grading.

The catalog starts with a bundled photograph, Compare/Move image tools, adjustment
sliders, pinch zoom, native zoom alternatives, quality, original/edited shortcuts
and reset. Compare drag moves the divider; Move drag pans. Pinch zoom works in
either tool. This slice previews only; import, full-resolution export, history,
recipe storage and backend delivery remain separate capabilities.

## Product preview

PreviewMesh admits owned non-indexed triangle data: seven finite floats per
vertex (position xyz, normal xyz, material slot), at most 100000 vertices, vertex
count divisible by three, positions and normal components within [-10,10], normals
with squared length greater than 0.000001 and integral
slots 0/1/2 for selected finish, neutral detail and emissive detail. It normalizes
normals at shading time. Invalid data is refused before GPU allocation.

OrbitCamera owns yaw wrapped to [-pi,pi), pitch clamped [-0.8,0.8], distance
clamped [2.5,7], default yaw 0.35/pitch 0.15/distance 4.5. Nonfinite inputs default.
Normalized drag rotates; pinch changes distance by its inverse scale. Native
rotate/zoom buttons provide alternatives. Reset restores the camera.
ProductFinish has porcelain, cobalt and bronze parameters. The exemplar is a
repository-authored triangle-mesh desk lamp with actual depth-tested geometry,
studio directional lighting, GGX specular response and ambient fill. A contact
shadow is a presentation approximation, not a shadow-map or physical simulation.
The asset format is a deliberately small internal triangle interchange, not a
glTF/OBJ importer. General scene graphs, AR, HDR environments and animation rigs
remain separate adapters/capabilities.

The catalog offers finish, camera gestures/buttons, reset, optional turntable,
quality and reduced-motion preview. Turntable defaults off. Reduced motion and
inactive lifecycle suppress automatic rotation, while static interaction still
redraws. Material/camera changes preserve mesh buffers.

## Lifecycle and failures

Use the existing quality budgets and active-time policy from
[GPU effects](gpu-effects.md). Draw only on changes unless turntable is active.
Compile programs/upload assets once per attachment/context, rebuilding on Android
context recreation. New asset identity replaces only its renderer resources.
All GPU operations stay on the native renderer thread. Callbacks publish on main
and stop after disposal. Expected asset admission/load failures use kernel values;
unexpected pipeline errors retain original diagnostics and project public copy.
Controls remain usable with opaque panels independent of the Glass capture path.

## Evidence

Shared fixtures verify bounded adjustments/viewports/cameras and malformed mesh
admission. Native Metal tests render real image/mesh pixels, comparing original/
edited, camera and finish changes; Android tests use PixelCopy for the external
surface and exercise tools, quality, lifecycle and navigation. iOS hosted tests
check native sizing/static updates and cleanup. Builds alone do not establish
visible output. Simulator results are not physical-device performance evidence.

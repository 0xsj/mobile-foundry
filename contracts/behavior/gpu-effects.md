# Native GPU effects

Status: Established for the first graphics slice, 2026-10-08.

## Scope and ownership

FoundryGraphics/core:graphics owns a bounded GPU canvas, shader programs, native
surface attachment, drawing, elapsed animation time and resource lifetime.
The catalog owns selection and native controls. Graphics depends on kernel,
not services, repositories or UI themes. No engine/asset/network/sync abstraction
is introduced. This is a procedural-effects exemplar, not a complete 3D engine.

Ripple and Orbit are selectable fullscreen fragment effects. Ripple renders a
wave field around a normalized interaction point. Orbit ray-marches a lit sphere
and orbiting ring; touch steers their orientation. Native shader languages need
equivalent behavior, not identical pixels. Both canvases accept top-left normalized
coordinates clamped to [0,1], defaulting to the center. Nonfinite input is rejected
to the default. Strength is clamped to [0,1], default 0.65 for nonfinite values.

## Product use cases

The gallery also exposes Flow (ambient backdrop), Material (reflective card),
Liquid (progress gauge), Particles (finite celebration), and Field (scalar density).
Shader indices are explicit: Ripple 0, Orbit 1, Flow 2, Material 3, Liquid 4,
Particles 5 and Field 6. Enum declaration order is not an upload protocol.

Flow and Material accept the normalized focus. Material is a bounded procedural
surface, not a mesh/PBR asset. Liquid takes feature-supplied `progress` in [0,1]
(default 0.5, including nonfinite input); its clock animates waves only. Empty
and full progress have no waves that imply progress outside those endpoints.
Particles use that same bounded input as a deterministic finite playhead: zero
and one are clear, intermediate values show an analytic 48-particle burst.
There is no automatic loop. The catalog owns Replay, a 2.4-second foreground,
pause-aware event that advances the playhead. Scrubbing cancels Replay. Reduced
motion disables automatic playback but permits explicit static scrubbing.

Field takes an owned snapshot of at most 12 samples (first 12 retained), each
with a normalized point, weight clamped to [0,1] (nonfinite defaults to 0), and
radius clamped to [0.03,0.5] (nonfinite defaults to 0.18). Empty/zero-weight data
renders a neutral grid. Samples are supplied data, not random shader noise.
Catalog Clusters/Trail fixtures, Add at focus and Clear data demonstrate this
input boundary; no measurements, geospatial accuracy, or physical simulation
are claimed. Field uses static redraws only. All examples use bounded shader
loops and existing resolution budgets; no new engine dependency is required.

## Resolution and time

Economy targets 30 submissions/second at half native pixel resolution, with a
900-pixel longest-edge cap and 32 marching steps. Balanced targets 60 at three
quarters resolution, with a 1600-pixel cap and 64 steps. Dimensions preserve
aspect ratio and floor to at least one pixel. Zero/unavailable view bounds skip
rendering. Targets are budgets, not measured performance guarantees.

Elapsed time advances only during requested animation in an active foreground
screen without reduced motion. The first frame after attach/resume establishes
a monotonic baseline; a delta is clamped to [0,0.1] seconds. Pausing or backgrounding
clears that baseline while retaining elapsed phase. Static touch/selection/quality
changes still request one redraw. Reset focus restores the interaction point;
it does not restart the renderer or implicitly resume animation.

## Native adapters and failure

iOS uses MTKView/Metal. Compile the bundled Metal source and create the pipeline
once per attachment, never per frame. Release drawables/delegate/resources on
removal. Android uses a GLSurfaceView/EGL ES 2 context and a Choreographer paced
requestRender loop. No continuous unbounded GL render mode. Lifecycle/removal
stops scheduling; pause releases its EGL context, and recreation rebuilds programs.
Only the GL thread accesses program/uniform handles. UI settings cross that
boundary as immutable values; callbacks return to the main thread and are ignored
after disposal. External surfaces are not a Compose backdrop source.

Missing GPU capability returns unavailable. Shader/pipeline failures report their
diagnostic identity at the injected callback and show a public internal failure.
There is no fake CPU animation labeled as a successful GPU renderer. The catalog
offers a clear unavailable/error presentation when initialization fails.

## Controls and evidence

The GPU effects destination exposes effect, quality, strength, animation pause,
reduce-motion preview and Reset focus. Touch affects the canvas; controls remain
native and usable when animation is paused. Replacing settings preserves the
surface/renderer instance. Background/resume and navigation removal/reopening
are explicit lifecycle scenarios.

Statistics describe submitted frames, render-target dimensions and approximate
submission rate, published at most once per second during animation, plus static
redraws. They do not claim GPU execution time, display FPS or physical-device
thermal/battery behavior. Shared fixtures check coordinate, sizing and clock
policy. Native checks must compile and execute shaders, inspect nonblank/different
effect pixels, and exercise lifecycle and controls. Simulator/emulator results
do not establish production performance on physical devices.

# Renderer frame ownership

A feature supplies bounded values and receives meaningful events; the native
renderer owns frame scheduling, GPU resources and transient animation time.

## Origin and evidence

The first GPU slice on 2026-10-08 follows the repository's optional rendering
boundary. Shared fixtures check admission, resolution and elapsed-time policy.
Native tests execute Metal/GL shaders and inspect pixels; Android checks context
recreation and removal. These establish the exercised native paths, not sustained
performance, thermal behavior or a general engine abstraction.

## What and why

A view update is not a frame callback. Compiling shaders or constructing a new
surface on each control change would discard resources and animation phase.
Attach once, compile once for that attachment/context, and update a small immutable
settings snapshot. A context loss invalidates its handles even when the integer
values still exist in application memory; rebuild them before drawing again.

Keep application services, database queries and observable UI state outside the
frame loop. The current loop reads settings, advances a local clock, uploads two
four-component uniforms and submits a fullscreen triangle. Stats return to the
main thread at a bounded frequency. A GPU handle is not a durable scene value.

The clock tracks active time rather than wall time since screen creation. Pause
clears its previous timestamp while retaining phase. The first resumed frame
sets a new baseline and adds zero; later deltas are clamped to 100 ms. Otherwise
a short background trip could cause a large visible jump on return.

## Example

Conceptual flow: drag → normalized point → settings snapshot → queued native
update → redraw. Pausing disables continuous scheduling but still allows that
redraw. A reduced-motion preference also suppresses animation without removing
the canvas or making its native controls unusable.

Quality changes three independent costs: submission target, render resolution,
and ray-march steps. Reducing only submission rate leaves each expensive frame
expensive. A fullscreen fragment shader evaluates every target pixel, so target
dimensions matter even when there is only one triangle.

## Gotchas

- A successful command submission does not prove displayed FPS or GPU duration.
  A submitted-frame counter must describe exactly that observation.
- An offscreen shader test can pass while the hosted view allocates a 1×1 target.
  Verify native bounds, actual drawable dimensions and visible pixels separately.
- Stopping a timer is insufficient if a queued callback can publish after removal.
  Disposal gates callback publication as well as stopping scheduling.
- A native surface rendered outside a UI capture layer cannot automatically
  become that layer's glass backdrop. Integrate capture explicitly when needed.

## Used in and related

Read the [Swift graphics walkthrough](../../frontend/swift/notes/modules/packages/FoundryGraphics/README.md)
and [Kotlin graphics walkthrough](../../frontend/kotlin/notes/modules/project/core/graphics/README.md),
then compare [material backdrop ownership](material-themes-and-backdrops.md).
The [GPU contract](../../contracts/behavior/gpu-effects.md) owns promised behavior;
future scene editing, assets and persistence need their own contracts.

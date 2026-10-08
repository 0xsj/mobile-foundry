# Graphics inputs and finite event time

Claim: decorative phase, feature truth and finite event time need different
owners even when the same fullscreen GPU pipeline renders them.

## Origin and evidence

The 2026-10-08 gallery expansion adds Flow, Material, Liquid, Particles and Field
beside Ripple/Orbit. The user wanted foundations that transfer across verticals.
The [contract](../../contracts/behavior/gpu-effects.md#product-use-cases) and
[blueprint](../../docs/blueprints/gpu-use-cases.md) define the bounded first pass.
Native value and pixel tests are the executable evidence; platform walkthroughs
record catalog execution and its limits. These are procedural examples, not a
general particle engine, live analytics feed or physically calibrated material.

## What and why

| Input | Owner | Meaning |
| --- | --- | --- |
| Renderer active time | Native canvas | Decorative flow, reflection drift, liquid waves; excludes pause gaps. |
| Liquid progress | Feature | Actual completion supplied to the renderer; time cannot increase it. |
| Particle playhead | Feature event | Finite normalized burst, scrubbable/replayable without a perpetual GPU loop. |
| Field samples | Feature | Owned point/weight/radius snapshot; empty data stays empty. |

Liquid can reuse the gauge for an upload or a timer, but the upload/timer owns
what 50% means. Waves must not fake successful completion. The catalog's
Empty/Half/Full controls demonstrate that seam with no backend claims.

Particles use analytic trajectories from a deterministic index seed. A feature
can supply any playhead from zero to one. Both endpoints clear the burst, and
renderer time does not affect a fixed playhead. Replay is a catalog event that
advances that value over 2.4 active seconds. Swift's keyed task and Compose's
LaunchedEffect cancel on pause, lifecycle change or selection; a fresh clock
baseline resumes from the retained playhead. A replay generation restarts an
already-playing burst. Scrubbing cancels playback. Reduced motion permits
static frames and prevents Replay. No service or GPU handle enters this logic.

Field sums radial scalar contributions, then maps density to color and contours.
The shader receives supplied samples rather than generating convincing-looking
random data. Twelve samples plus three vec4 control uniforms fit within the
ES 2 minimum of sixteen fragment uniform vectors documented by
[Khronos GLSL ES 1.00, section 7.8](https://www.khronos.org/files/opengles_shading_language.pdf).
This bounds the exemplar, not a scalable large-data strategy. Textures or a
different backend would be justified by a larger measured workload.

## Example

Conceptual workflow: a goal feature publishes completion 0.42; Liquid draws that
level while its optional native clock changes only the waves. On goal completion,
the feature decides whether to trigger a celebration, then supplies a bounded
particle playhead. A reduced-motion presentation can retain a chosen static
frame or native success copy. The renderer does not decide what counts as a goal.

## Gotchas

- A recurring fractional-time burst would replay forever without a new event.
  The finite input avoids that accidental product behavior.
- Pause must clear the monotonic baseline; elapsed background wall time must
  not jump the playhead to completion on resume.
- A new Replay while playing needs a new task identity, not just a true flag.
- Cancellation can be applied after a queued frame callback. The Android device
  test exposed a scrubbed value overwritten by that callback. Check current
  playback/generation before publishing, as well as canceling the keyed task.
- Swift arrays own value snapshots. Kotlin's read-only List alone is not
  ownership: admission copies into an unmodifiable list, and private generated
  copy visibility keeps consumers from bypassing the factory.
- Settings changes retain the native pipeline. Particles and Field request
  redraws from input changes; their GPU animation scheduler stays static.
- Native progress/sample/playhead captions describe supplied input. Submission
  counters are not GPU timings or display-rate guarantees.
- Forty-eight particles per fragment is bounded but can still be expensive.
  Simulator/emulator success does not establish battery or sustained frame cost.

## Used in

- Swift [settings](../../frontend/swift/packages/FoundryGraphics/Sources/FoundryGraphics/EffectSettings.swift),
  [pipeline](../../frontend/swift/packages/FoundryGraphics/Sources/FoundryGraphics/Metal/EffectPipeline.swift),
  [shader](../../frontend/swift/packages/FoundryGraphics/Sources/FoundryGraphics/Shaders/Effects.metal),
  and [catalog](../../frontend/swift/apps/FoundryCatalog/Sources/Graphics/GPUEffectsView.swift).
- Kotlin [settings](../../frontend/kotlin/project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/EffectSettings.kt),
  [renderer](../../frontend/kotlin/project/core/graphics/src/main/kotlin/dev/mobilefoundry/graphics/gl/EffectRenderer.kt),
  [shader](../../frontend/kotlin/project/core/graphics/src/main/assets/foundry_graphics/effects.frag),
  and [catalog](../../frontend/kotlin/project/app/src/main/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreen.kt).
- Shared [admission fixtures](../../contracts/fixtures/graphics/effects.json),
  Swift [actual Metal pixels](../../frontend/swift/packages/FoundryGraphics/Tests/FoundryGraphicsTests/MetalEffectsTests.swift),
  and Android [device workflows](../../frontend/kotlin/project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/graphics/GPUEffectsScreenTest.kt).

## Related and next questions

[Frame ownership](renderer-frame-ownership.md) explains native scheduling;
[editable values and resources](editable-values-and-renderer-resources.md)
explains retained assets. Which feature event should trigger a celebration?
When does a sample list justify a density texture? How would physical-device
GPU time change the particle budget?

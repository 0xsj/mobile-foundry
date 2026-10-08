# GPU effects construction specification

## Status and scope

Implemented, 2026-10-08. A GPU effects gallery establishes the first
native render boundary. The user requested GPU work after native forms/themes.

## Exemplar workflow

Open GPU effects, drag the canvas, select Ripple/Orbit, adjust strength/quality,
pause, interact while static, background/resume and navigate away/reopen.
No product data, assets, backend or complete scene engine is required.

## Source documents and authority

[Architecture](../../ARCHITECTURE.md#graphics-integration),
[organization](../ORGANIZATION.md), [GPU contract](../../contracts/behavior/gpu-effects.md),
and [kernel failures](../../contracts/behavior/kernel.md) are established inputs.
Metal/GLSurfaceView are selected native adapters for this exemplar. Persistence
and backend transport are not applicable. Physical-device performance is unknown.

## Nouns, states, and invariants

EffectSettings is an immutable bounded snapshot; EffectPoint has normalized
top-left coordinates. EffectQuality owns resolution/frame/step budgets.
EffectClock owns phase and monotonic-baseline rules. EffectStatistics describes
submissions, not GPU timing. EffectEvent reports ready/statistics/failure.
The native adapter owns all GPU handles and refuses publication after disposal.

## Workflow and side-effect ordering

Admit settings → attach native surface → compile/link once → size target → draw
fullscreen triangle → publish throttled statistics. Main-thread controls never
touch GPU handles. Pause stops the frame scheduler before releasing resources;
resume recreates Android resources and resets only the time baseline. Metal
commands retain resources until completion. No task or shader compile per frame.

## Expected tree and file specifications

Paths below are relative to the native platform root. All entries are required.

| Swift / Kotlin file | Owner, symbols, dependencies and verification |
| --- | --- |
| `packages/FoundryGraphics/Package.swift` / `project/core/graphics/build.gradle.kts` | Optional graphics library; kernel plus native frameworks/Compose/lifecycle; no UI/service dependency. Wire into app/build checks. |
| graphics source `EffectSettings.swift` / `EffectSettings.kt` | EffectKind/Quality/Point/Settings/Statistics/Event, pure admission/resolution; shared fixtures. |
| graphics source `EffectClock.swift` / `EffectClock.kt` | Monotonic elapsed policy, suspend/reset baseline; shared timeline fixture. |
| graphics source `Metal/EffectPipeline.swift` / `gl/EffectRenderer.kt` | Compile/link, uniform upload and fullscreen drawing; original diagnostic callback plus public failure; native execution/pixel evidence. |
| graphics source `Metal/MetalEffectSurface.swift` / `gl/GPUEffectSurface.kt` | Native bridge, touch, scheduler/lifecycle/disposal, main-thread callbacks; native interaction and lifecycle checks. |
| graphics resources `Shaders/Effects.metal` / `src/main/assets/foundry_graphics/effects.vert` and `effects.frag` | Independent Metal/GLSL implementations of two effects; uniform layout documented; compile and differing nonblank pixels. |
| graphics tests `EffectPolicyTests.swift` / `EffectPolicyTest.kt` | Shared bounded inputs, resolution and pause/resume timeline. |
| graphics tests `MetalEffectsTests.swift` / app device `ui/graphics/GPUEffectsScreenTest.kt` | Actual shader execution, framebuffer pixels, native controls, pause/navigation. |
| iOS app `Tests/GPUEffectsLayoutTests.swift` | Hosted native bounds/texture and automatic-frame dimensions, paused quality updates, resume and disposal; added after integration evidence. |
| app `Sources/Graphics/GPUEffectsView.swift` / `ui/graphics/GPUEffectsScreen.kt` | Catalog composition, controls, failure/public copy and throttled metrics; no GPU handles. |
| graphics `README.md` and mirrored module notes | Source walkthrough, actual tests, limitations and native language/framework mechanics. |

Existing project.yml/settings/app dependencies, navigation/home, Makefile,
README/architecture/organization/setup, contracts index, shared/platform reading
indexes and AGENTS capability list are updated. Required shared fixture:
`contracts/fixtures/graphics/effects.json`. Required explanatory notes: shared
frame ownership plus native Metal and GL thread/lifecycle substrate mechanics.

## Cross-file dependency map

Catalog → graphics surface → native renderer → bundled shader.
Policy/clock → kernel values only. UI tokens are consumed only by the catalog.
No new third-party dependency is required.

## Transport and persistence mapping

Not applicable. Frame state, GPU handles and counters are transient and scoped
to the canvas. Resource loading is local; shader text compiles once per attachment
or recreated context. Build-time Metal precompilation can be evaluated later.

## Verification plan

Run native policy tests and real offscreen Metal execution, both app builds,
Android device controls/framebuffer/lifecycle checks and manual iOS shader/UI
checks. Inspect actual pixels, not only the selected effect name. Run notes-check
and diff checks. Do not present simulator submission rates as device benchmarks.

## Construction order

Contract/fixtures → policies → shaders/pipelines → native surfaces/lifetime →
catalog/navigation → native evidence → learning handoff.

## Open decisions and unknowns

Real-device frame time/thermal targets, Vulkan/Filament/RealityKit, mesh/assets,
external renderer glass integration, durable scene values and app-data events
remain separate work. The native shader interfaces protect a real rendering
boundary without imposing a universal engine interface.

## Deviation record

The specification's native shader/UI check was strengthened with an iOS hosted
canvas test after manual inspection found a stretched 1×1 drawable despite
passing offscreen pixels. Explicit representable sizing, layout-time target
updates before frame acquisition, and screen-derived scale corrected it. An
intermediate view-scale assertion failed; the final stable-resolution check passes.
No new engine dependency or application-data boundary was introduced.

## Completion evidence

Both app builds pass. `make graphics-test` passes two policy tests per platform
and one actual Metal execution/pixel test on this Mac. `make ios-test` passes all
14 checks, adding native bounds/texture and automatic-frame dimensions,
pause/resume and dismantling. `make android-test` passes and all 25 device checks
pass on API36_Test/Android 16, adding real framebuffer pixels/touch/quality,
foreground context recreation/disposal and navigation/reduced-motion controls.
Manual iOS screenshots show Ripple and the lit Orbit; detailed interaction
evidence is in the native catalog notes. Notes and diff checks pass.

Physical-device GPU time, sustained rate, battery, thermals, richer scene assets,
and external-surface glass capture remain unmeasured or unimplemented. Submission
statistics are deliberately labeled and do not complete those performance goals.

## Completion criteria

Both catalogs render touch-driven GPU effects, stop offscreen, support paused
interaction/reduced motion and resource recreation, and expose honest evidence.
Notes explain the new mechanisms and distinguish runtime checks from device targets.

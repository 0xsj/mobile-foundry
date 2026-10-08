# GPU product use cases construction specification

## Status and scope

Implemented 2026-10-08. Extend the existing native GPU gallery with five reusable
use cases; keep native render/resource ownership and dependencies intact.

## Exemplar workflow

Select Flow and steer its backdrop; drag Material to move its reflection;
scrub Liquid from empty to full; replay and scrub Particles; load, edit and clear
Field samples. Pause/reduced motion retain static controls and native fallbacks.

## Source documents and authority

[GPU contract](../../contracts/behavior/gpu-effects.md#product-use-cases),
[architecture](../../ARCHITECTURE.md#graphics-integration), and the existing
[renderer blueprint](gpu-effects.md) are established. No transport, persistence,
engine, asset ingestion, or backend measurement is needed. Device cost is unknown.

## Nouns, states, and invariants

EffectKind owns explicit shader indices. EffectSettings adds bounded progress
and an owned field snapshot. EffectFieldSample admits point/weight/radius.
Particles are a pure function of supplied playhead; event timing belongs to the
catalog. Replay has idle, playing, paused and completed states; switching effect
or scrubbing cancels it. Reduced motion/background pauses advancement without
catch-up. One supplied playhead renders identically regardless of renderer time.

## Workflow and side-effect ordering

Admit immutable values before upload. Retain the pipeline and shader handles.
Catalog Replay advances the playhead at the selected frame budget while visible,
active and motion-enabled; a monotonic bounded delta excludes pause gaps.
GPU surfaces use static redraws for Particles/Field; no second GPU animation
loop runs behind Replay. Changing input draws once, including reduced motion.

## Expected file tree and file specifications

All paths below are required, relative to the repository root. Existing files
are extended; only the explanatory note and this blueprint are new modules/docs.

| Files | Symbols, ownership, dependencies, ordering and checks |
| --- | --- |
| Swift graphics `EffectSettings.swift`; Kotlin graphics `EffectSettings.kt` | Kind indices, EffectFieldSample admission, EffectSettings progress/owned bounded samples; no app/UI dependency; fixtures check nonfinite/clamps/ownership. |
| Swift `Metal/EffectPipeline.swift`; Kotlin `gl/EffectRenderer.kt` | Three vec4 uniforms plus 12 sample vec4s; reuse pipeline and retained sample upload memory; no product events or per-frame asset work. |
| Swift `Shaders/Effects.metal`; Android `foundry_graphics/effects.frag` | Five bounded shade branches: flow, rounded material, clipped liquid, analytic particles, supplied density. Compile and inspect actual pixels, endpoints, input changes and independence from time. |
| Swift catalog `GPUEffectsView.swift`; Kotlin catalog `GPUEffectsScreen.kt` | Native selection/use-case copy/progress/scrub/replay/sample controls. Own Replay clock and lifecycle; reuse existing native surface. Expose percentage/sample/playhead captions for accessible alternatives. |
| `contracts/fixtures/graphics/effects.json`; native `EffectPolicyTests.swift`/`EffectPolicyTest.kt` | Shared indices/admission cases and retained snapshot checks. |
| Swift `MetalEffectsTests.swift`; Android `GPUEffectsScreenTest.kt`; iOS `GPUEffectsLayoutTests.swift` | Native pixels for all branches and changed inputs, pause/lifecycle tests and finite Replay/control workflow; preserve existing checks. |
| `notes/patterns/graphics-inputs-and-event-time.md`; shared/native indexes and graphics/catalog module notes | Explain decorative time versus feature truth versus finite event playhead, bounded data/upload and verified limits. |
| graphics READMEs, root README and `docs/SETUP.md` | Document current gallery, actual controls and reusable inputs. |

## Cross-file dependency map

Catalog state → admitted graphics snapshot → retained native pipeline → shader.
Catalog event clock → supplied particle playhead. Field samples → uniform upload.
No changes to the independent image/mesh preview API or resource lifetimes.

## Transport and persistence mapping

Not applicable. Catalog fixtures and event state are local/transient examples.

## Verification plan

Native value fixtures, real Metal offscreen execution for all five branches,
Android PixelCopy for all five and controls, hosted iOS bounds/lifetime tests,
both app builds and notes validation. Endpoints/time independence must be tested,
not inferred from screenshots. Physical-device profiling remains separate.

## Construction order

Contract/blueprint → values/fixtures → uploads/shaders → native catalog workflows
→ shader/device evidence → learning notes and actual completion evidence.

## Open decisions and unknowns

Physical-device power/frame cost, compute-particle systems, measured geodata,
real PBR material assets and product/backend progress semantics are deferred.

## Deviation record

The initial sample cap of 16 was reduced to 12 before shader implementation.
Three control vectors plus twelve sample vectors fit the ES 2 minimum fragment
uniform budget of sixteen. The authoritative contract and fixtures now use
twelve; no dependency was added. The first Android device run exposed a queued
Replay frame overwriting a scrubbed input. Publication now checks current
playback/generation as well as relying on keyed-task cancellation.

## Completion criteria

All five examples are selectable on both platforms, respond to supplied inputs,
respect pause/reduced motion/lifecycle and retain native controls. Tests execute
shaders and verify outputs; notes give an asynchronous source reading path.

## Completion evidence

The shared graphics command passes five value/policy tests per platform plus
two actual Metal execution tests. The effect pixel test now executes all seven
branches, checking progress endpoints, fixed particle playhead/time independence,
field input changes and neutral empty/zero-weight data.

Both catalogs build; Android host regressions and all fifteen iOS app checks
pass. The first Android device run passed thirty of thirty-one tests and exposed
a Replay publication race. After the guard correction, all five focused GPU
checks and the complete thirty-one-check suite pass on API36_Test/Android 16.
They cover retained surfaces, real framebuffer pixels, data/progress controls,
reduction, pause/background, finite completion/restart and scrub cancellation.

Manual iPhone 17 Pro/iOS 26.2 inspection confirms seven menu options, Liquid
Empty/Half/Full, finite Replay completion and disabled reduced-motion Replay,
and Field clear/add/trail counts. CLI screenshots show Flow, Material, Liquid
and Field. Native accessibility setValue changed only the exposed slider value,
not SwiftUI state, so that attempt is not counted as an iOS scrub interaction.
Shader tests check supplied playheads; Android tests exercise actual scrub state.
Final notes/assets/diff checks pass. Device profiling, exhaustive accessibility
and larger particle/data workloads remain open.

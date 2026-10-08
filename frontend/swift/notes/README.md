# Swift learning notes

Read the Swift implementation through its module walkthroughs, then use the
linked language and tool notes to understand the choices. This notebook follows
[the shared workflow](../../../docs/NOTES.md) and Bento's categories.

## Reading order

1. [Swift package and Xcode project wiring](substrate/swift-package-and-xcode-project-wiring.md).
2. [Protocols and opaque return types](language/swift-protocols-and-opaque-return-types.md).
3. [Catalog shell walkthrough](modules/apps/FoundryCatalog/README.md), following
   the source links from app composition to the displayed list.
4. [Result and failure values](language/swift-result-and-failure-values.md), then
   [the kernel walkthrough](modules/packages/FoundryKernel/README.md).

5. [Async request mechanics](language/swift-async-ports-and-continuations.md), then
   [HTTP](modules/packages/FoundryHTTP/README.md) and [health services](modules/packages/FoundryServices/README.md).
6. [Native request and screen lifetime](substrate/urlsession-and-view-task-lifetime.md), then revisit
   the catalog walkthrough to trace its injected scenarios.
7. [Observable stores and service protocols](language/swift-observable-stores-and-service-protocols.md),
   then the services/catalog walkthroughs' notes sections and [provider seams](../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).
8. [Generic state and content builders](language/swift-generic-query-state-and-content-builders.md),
   then [FoundryQuery](modules/packages/FoundryQuery/README.md),
   [FoundryUI](modules/packages/FoundryUI/README.md), and
   [query/rendering ownership](../../../notes/patterns/query-state-and-rendering.md).
9. [Token environment and scope](substrate/swiftui-token-environment.md), then
   revisit [FoundryUI](modules/packages/FoundryUI/README.md) for the token families,
   V1 mapping, provider, tests, and catalog consumer. Compare
   [semantic token ownership](../../../notes/patterns/semantic-tokens-and-native-themes.md).
10. [Material themes and backdrop ownership](../../../notes/patterns/material-themes-and-backdrops.md),
    then [native glass availability and scope](substrate/swiftui-token-environment.md#glass-material-extension)
    and the UI walkthrough's material slice.
11. [Form focus and submission](substrate/swiftui-form-focus-and-submit.md), then
    the new services/query/UI/catalog sections and
    [shared mutation ownership](../../../notes/patterns/forms-and-mutation-ownership.md).
    Compare native focus bindings, admitted commands, actor writes and late-result guards.
12. [MetalKit surface and shader lifetime](substrate/metalkit-surface-and-shader-lifetime.md),
    then [FoundryGraphics](modules/packages/FoundryGraphics/README.md) and the
    catalog's GPU section. Compare native bounds/drawables, SIMD uniform layout,
    frame clocks and [shared ownership](../../../notes/patterns/renderer-frame-ownership.md).
13. [Metal textures and meshes](substrate/metal-preview-textures-and-meshes.md),
    then [the preview walkthrough](modules/packages/FoundryGraphics/README.md#product-previews)
    and both catalog features. Compare image encoding/coordinates, vertex packing,
    camera projection and [editable values](../../../notes/patterns/editable-values-and-renderer-resources.md).

14. Read [graphics inputs and finite event time](../../../notes/patterns/graphics-inputs-and-event-time.md), then revisit
    the graphics and catalog walkthroughs. Compare decorative phase, supplied
    progress/data and a finite feature-owned celebration playhead.

## Findings by lifespan

| Directory | Subject |
| --- | --- |
| `modules/<source-path>/` | Local code flow, decisions, and verification |
| `language/` | Transferable Swift mechanics |
| `patterns/` | Architectural approaches specific to the Swift implementation |
| `concepts/` | Domain distinctions needing a Swift-specific explanation |
| `techniques/` | Swift investigation and verification methods |
| `substrate/` | Versioned Xcode, SwiftUI, SDK, package, and tool behavior |

Paths mirror the Swift root: `apps/FoundryCatalog/` maps to
`notes/modules/apps/FoundryCatalog/`. Module notes link source and checks;
transferable notes describe the mechanism without local source paths.

15. Read [compositing and pass ownership](../../../notes/patterns/premultiplied-compositing-and-render-passes.md), then [graphics measurement](../../../notes/techniques/graphics-profiling-and-measurement.md). Revisit the graphics/catalog walkthroughs and native texture notes for
    transparent input, linear premultiplication, framebuffer passes and completion.

## Current coverage

The 2026-10-07 bootstrap links a SwiftUI app to an empty Swift kernel library.
The package and iOS simulator builds passed. The catalog shows planned areas;
it has no feature navigation, service calls, or renderer yet.

The 2026-10-08 kernel slice implements typed outcomes, immutable failures, and
public projection. Seven host tests passed against shared fixtures. The
language note includes the verified rejection of a throwing Result.map callback.
Shared reasoning is in [expected failures and diagnostics](../../../notes/concepts/expected-failures-and-diagnostics.md).
The HTTP slice on 2026-10-08 adds request admission, native transport,
problem/JSON decoding, deadlines, health services and six interactive catalog
scenarios. Both platforms consume the same response/path/retry fixtures.
Thirteen HTTP and two service host tests passed; the simulator app built, and Healthy, Malformed, and Timeout presentation were observed on iOS 26.2.
Diagnostic observers receive original dependency errors; durable reporting
infrastructure remains separate. Session, persistence, sync and graphics are
future capabilities.

The 2026-10-08 notes/query slice adds a provider-independent NotesService port,
memory and HTTP adapters, an observable main-actor store, and one list renderer.
Five service package tests (three new) and seven iOS store tests passed. The
simulator accessibility state confirmed memory/HTTP content and HTTP empty/
unavailable presentation. HTTP remains injected; screenshots were blank in this
session, so visual layout and assistive-technology quality remain unverified.

The 2026-10-08 query/UI extraction adds generic value state and native async
presentation. Two query package tests passed against nine shared cases, and
all seven iOS store regressions still pass. The app and UI package compiled
through `make ios-test`. Simulator interactions confirmed initial/retained
states, empty retention, public failure copy, Retry and Cancel. A current
screenshot showed the populated gallery's native layout; comprehensive
accessibility, text-size and appearance checks remain future work.

The 2026-10-08 token slice adds V1 light/dark semantic tokens, native type/space/
shape/motion roles, and a scoped SwiftUI theme. Three UI package tests pass for
shared fixtures, contrast, and reduction; the final simulator app builds and
seven store regressions pass. Manual iOS 26.2 checks confirm scoped appearance,
reduced durations, action/position changes, and retained sample state after
Dark → Light. A screenshot shows dark examples beneath light catalog controls.
Exhaustive Dynamic Type, VoiceOver, iOS hit measurement, and devices remain open.

The same day's [Foundry Studio revision](../../../STYLES.md#design-direction-foundry-studio)
replaces the borrowed palette/scales with porcelain/graphite/cobalt, a four-point
rhythm, semibold native headings, and decelerating motion. Three revised token
tests and the iOS app build pass; the UI walkthrough records the changes and
simulator semantic checks. Screenshot capture was blank during the revision.

The same day's material slice adds app/local Solid/Glass selection, separate
content/floating roles, native glass with older-system Material fallback, and
transparency reduction. Four UI package tests pass and the app builds. Simulator
semantic checks confirm inheritance, local selection, opaque reduction, and
retained scene state; screenshots remain blank. Read the UI walkthrough for
source links and verification limits. A renderer bridge and device performance
remain future work.

The same day's forms/mutation slice adds CreateNote/NoteCreator, actor memory
and HTTP adapters, MutationState, native form controls and a guarded observable
store. Nine service and three query tests pass, both including shared fixtures;
all thirteen iOS app tests pass on iPhone 17 Pro/iOS 26.2. The final app build
passes. Manual native interactions confirm blank-input feedback, Return submit,
confirmation/reset, provider-swapped drafts, server title refusal and recovery.
CUA screenshot output remains blank, but simctl capture produced a readable
layout screenshot that was inspected. The framework note records a Swift 6.2.3
IR-generation crash resolved by an explicit Binding setter closure. Persistent
drafts, remote idempotency and comprehensive accessibility/device checks remain open.

The Glass follow-up corrects the outer form panels' surface role and adds a
catalog backdrop. The final build passes; Solid/Glass simulator captures confirm
the visible material change with readable native input. See
[material ownership](../../../notes/patterns/material-themes-and-backdrops.md#gotchas)
and the catalog walkthrough for the cause and usage correction.

The GPU slice adds optional FoundryGraphics with Ripple/Orbit shaders, bounded
settings and active-time policies. Two policy tests and one actual native Metal
pixel test pass via `make graphics-test`; both native apps build. All 14 iOS app
tests pass, including hosted bounds/texture/automatic-frame dimensions, paused
quality updates, resume and disposal. The walkthrough records a 1×1 drawable
correction and the intermediate view-scale assertion failure. Simulator images
show both effects; native controls exercise pause/strength/reduction. Physical
devices and iOS touch/accessibility assessment remain open. Read
[the graphics walkthrough](modules/packages/FoundryGraphics/README.md).

## Product preview follow-up

The product-preview follow-up adds two consumers of MetalPreviewSurface:
Image studio and Product studio. Four Swift graphics value/policy checks plus
two native Metal execution checks pass; all 15 iOS app checks pass, including
both hosted feature canvases and static redraw/disposal. Simulator captures show
the upright photograph and lit lamp. Read [the preview walkthrough](modules/packages/FoundryGraphics/README.md#product-previews),
[texture/mesh mechanics](substrate/metal-preview-textures-and-meshes.md), then
[editable values/resource ownership](../../../notes/patterns/editable-values-and-renderer-resources.md).
Import/export, general scene loading, durable recipes and device profiling remain open.

## Questions for the next session

- Why is decoded asset identity distinct from a durable asset ID?
- Why would choosing an sRGB texture require changing the filter's conversion policy?
- Why must manual drawable sizing precede Metal's frame acquisition?
- Why use screen scale independently of a manually reduced drawable?
- Why do submissions per second differ from GPU time or displayed FPS?
- Why can valid JSON still be invalid domain data?
- How does a request deadline differ from a screen canceling its work?
- Which layer should renew credentials and decide retries?
- How should an admitted write update observable reads without a screen owning storage?
- Why does missing confirmation differ from a refused command?
- Why does a nested theme read its incoming environment before supplying an override?
- Which query behaviors require a shared cache, and which belong to one feature's store?
- What does pure settlement omit that a feature must check before publishing?
- Why should a material change replace a background without replacing its content?

## GPU use-case expansion

The same day's expansion adds five procedural consumers: Flow, Material, Liquid,
Particles and Field. Read [graphics inputs and event time](../../../notes/patterns/graphics-inputs-and-event-time.md).
Five value/policy checks pass per platform plus two actual Metal execution tests.
Both apps build, Android host regressions and fifteen iOS app checks pass.
The corrected complete Android device suite passes all thirty-one checks; native
walkthroughs record the queued-frame scrub race and iOS manual evidence/limits.
The examples keep decorative time separate from supplied completion/data and a
finite feature-owned event. Physical-device budgets remain unmeasured.

## Compositor batch evidence

Completed 2026-10-08. Compositor studio extends the retained preview surface with
owned straight RGBA input, linear premultiplied upload, feathered masking,
Normal/Multiply/Screen blending, separable blur/glow, comparison and reset.
Optional profiles separate CPU encoding, available completed Metal GPU intervals
and estimated texture payload. GLES 2 GPU timing is explicitly unavailable.

Seven graphics value/fixture checks pass per platform, plus three actual Metal
render tests. Both native builds pass; all sixteen iOS app checks, seventeen
Android app host checks and the complete thirty-four-check Android device suite
pass. The final three focused compositor checks also pass after tightening GL
error classification. Hosted iOS checks cover actual studio layout and retained
surface/cache/resize/profile/pause/removal. Android checks additionally exercise
native editing, gestures, reset, context recreation and reopening with PixelCopy.

Manual iOS checks confirm Original/Composed, mask bypass with disabled mask
controls, repeated profiling, reduced-motion pause and reset to defaults. A
simulator CLI screenshot was inspected for the actual photograph, transparent
layer, comparison divider and native control layout. CUA screenshot capture is
still blank; the CLI image supplies visual evidence. iOS slider/VoiceOver and
physical-device thermal/energy budgets remain unmeasured. The profiling document
is a repeatable protocol, not a completed device benchmark.

Next questions: when do measured costs justify skipping passes, using half-float
targets or adding GPU timer queries; and how should a saved recipe version its
CPU assets/settings without storing native handles?

## App icon handoff

The 2026-10-08 [icon packaging note](substrate/swift-package-and-xcode-project-wiring.md#generated-app-icon)
explains original/derivative ownership, prompt provenance and the selected iOS
asset slot. The simulator build and asset checks pass. TestFlight distribution
is pending the remaining pre-release work.

## Placeholder shell reading order

Read [tab selection and presentation](substrate/swiftui-tab-selection-and-presentation.md),
then [the catalog shell](modules/apps/FoundryCatalog/README.md#four-tab-placeholder-shell)
and [shared lifetime ownership](../../../notes/patterns/tabs-and-feature-lifetime.md).
The component map is a directory sketch, not completed UI coverage. Next:
when should each real destination own a detail stack, and how should a deep
link select a tab before navigating within it?

Shell evidence: simulator build and sixteen existing app/layout checks pass;
manual iOS execution covers all tabs, material selection and catalog return.
The app walkthrough records the native capture and restoration limits.

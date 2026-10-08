# Kotlin learning notes

Follow the Android implementation through source walkthroughs, with separate
notes for Kotlin mechanics and framework behavior. This notebook follows
[the shared workflow](../../../docs/NOTES.md) and Bento's categories.

## Reading order

1. [Gradle and Android bootstrap](substrate/gradle-and-android-bootstrap.md).
2. [Sealed UI states and data classes](language/kotlin-sealed-ui-states-and-data-classes.md).
3. [Android starter walkthrough](modules/project/app/README.md), from the
   activity through navigation, repository, view model, and screen.
4. [Outcomes and owned payloads](language/kotlin-outcomes-and-owned-payloads.md),
   then [the kernel walkthrough](modules/project/core/kernel/README.md).

5. [Async request mechanics](language/kotlin-suspending-ports-and-cancellation.md), then
   [HTTP](modules/project/core/http/README.md) and [health services](modules/project/core/services/README.md).
6. [Native request and screen lifetime](substrate/okhttp-and-compose-effect-lifetime.md), then revisit
   the catalog walkthrough to trace its injected scenarios.
7. [Service interfaces and StateFlow](language/kotlin-service-interfaces-and-stateflow.md),
   then the services/catalog walkthroughs' notes sections and [provider seams](../../../notes/patterns/transport-service-and-screen.md#provider-seams-and-query-state).
8. [Covariant state and content slots](language/kotlin-covariant-query-state-and-content-slots.md),
   then [core/query](modules/project/core/query/README.md),
   [core/ui](modules/project/core/ui/README.md), and
   [query/rendering ownership](../../../notes/patterns/query-state-and-rendering.md).
9. [Token theme and touch bounds](substrate/compose-token-theme-and-touch-bounds.md),
   then revisit [core/ui](modules/project/core/ui/README.md) for token families,
   V1 mapping, Material adaptation, tests, and catalog controls. Compare
   [semantic token ownership](../../../notes/patterns/semantic-tokens-and-native-themes.md).
10. [Material themes and backdrop ownership](../../../notes/patterns/material-themes-and-backdrops.md),
    then [Compose backdrop layers](substrate/compose-backdrop-layers.md) and the
    UI walkthrough's material slice.
11. [Form focus and IME](substrate/compose-form-focus-and-ime.md), then the new
    services/query/UI/app sections and
    [shared mutation ownership](../../../notes/patterns/forms-and-mutation-ownership.md).
    Compare command admission, mutex snapshots, native keyboard actions and coroutine guards.
12. [GLSurfaceView and Compose lifetime](substrate/glsurfaceview-and-compose-lifetime.md),
    then [core/graphics](modules/project/core/graphics/README.md) and the app's GPU
    section. Trace immutable queued snapshots, GL handles, context recreation and
    [shared frame ownership](../../../notes/patterns/renderer-frame-ownership.md).
13. [GLES assets and gestures](substrate/gles-preview-assets-and-gestures.md),
    then [the preview walkthrough](modules/project/core/graphics/README.md#product-previews)
    and both app features. Compare asset identity, incremental state reads and
    [editable values/resource ownership](../../../notes/patterns/editable-values-and-renderer-resources.md).

14. Read [graphics inputs and finite event time](../../../notes/patterns/graphics-inputs-and-event-time.md), then revisit
    the graphics and catalog walkthroughs. Compare decorative phase, supplied
    progress/data and a finite feature-owned celebration playhead.

15. Read [compositing and pass ownership](../../../notes/patterns/premultiplied-compositing-and-render-passes.md),
    then [graphics measurement](../../../notes/techniques/graphics-profiling-and-measurement.md).
    Revisit the graphics/catalog walkthroughs and native texture notes.
16. Read [capture assets and preview lifetime](../../../notes/patterns/capture-assets-and-preview-lifetime.md),
    then [CameraX and photo admission](substrate/camerax-capture-and-photo-admission.md)
    and the [app camera walkthrough](modules/project/app/README.md#camera-and-shared-photo-editor).
    Trace explicit permission → owned use cases → closed ImageProxy → GPU editor.
17. Read [component slots and caller-owned state](../../../notes/patterns/component-slots-and-caller-owned-state.md),
    then the [UI component batch](modules/project/core/ui/README.md#everyday-component-batch-and-naming)
    and [everyday gallery](modules/project/app/README.md#everyday-component-gallery).
    Compare native content receivers, caller state, variants and composed patterns.

18. Read [selection and modal drafts](substrate/compose-selection-and-modal-drafts.md),
    then the UI and app walkthroughs' Controls/Overlays sections. Compare
    committed values, temporary date drafts, mixed selection, intermediate slider
    stops and native explicit confirmation.

19. Read [loading and passive content](substrate/compose-loading-and-passive-content.md),
    then the UI/app walkthroughs' Display/Feedback/Collections sections and
    [projection/lifetime reasoning](../../../notes/patterns/collection-projections-and-feedback-lifetime.md).
    Compare passive identity, reduced-motion animation, group field targets,
    visible selection scope and transient notice restoration.

20. Read [layout and contextual presentation](substrate/compose-layout-and-contextual-presentation.md),
    then the UI/app walkthroughs' Context/Layout sections. Compare native
    measurement, stable child identity, text scaling, anchored dismissal and
    caller-owned navigation/restoration.

21. Read [disclosure and bounded detail regions](substrate/compose-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08)
    and [widening before integer arithmetic](language/kotlin-widen-before-integer-arithmetic.md),
    then the [UI composition walkthrough](modules/project/core/ui/README.md#choices-disclosure-and-detail-composition)
    and [delivery preview](modules/project/app/README.md#details-and-delivery-preview-gallery).
    Compare draft lifetime, endpoint saturation, native fitting and a scrolling
    body with persistent actions.

22. Read [rich input and journey pages](substrate/compose-rich-input-and-journey-pages.md),
    then the [UI walkthrough](modules/project/core/ui/README.md#rich-input-and-journey-pages)
    and [Journeys gallery](modules/project/app/README.md#journeys-gallery).
    Compare native state-based input, ephemeral/saveable drafts, passive
    readiness, keyed pages and explicit advancement.

23. Read [refresh and lazy activity](substrate/compose-refresh-and-lazy-activity.md),
    then the [UI walkthrough](modules/project/core/ui/README.md#activity-and-paged-collections)
    and [Activity gallery](modules/project/app/README.md#activity-gallery).
    Compare awaited refresh, explicit paging, stable row identity and cancellation.

## Findings by lifespan

| Directory | Subject |
| --- | --- |
| `modules/<source-path>/` | Local code flow, decisions, and verification |
| `language/` | Transferable Kotlin mechanics |
| `patterns/` | Architectural approaches specific to the Kotlin implementation |
| `concepts/` | Domain distinctions needing a Kotlin-specific explanation |
| `techniques/` | Kotlin investigation and verification methods |
| `substrate/` | Versioned Android, Compose, Gradle, and dependency behavior |

Paths mirror the Kotlin root, including `project/`: `project/app/` maps to
`notes/modules/project/app/`. Reusable explanations stay independent of source
paths; module walkthroughs link their actual use.

## Current coverage

The seventh UI batch adds six activity/feed building blocks (51 total), a
native scrolling destination and explicit refresh/page fixture. Reading step 23
links source, checks, native differences and limits.

Rich input/journey batch, 2026-10-08/09: six APIs bring the six component batches
to 45 building blocks. Both apps build; 22 iOS app checks, nineteen focused
Android checks and four UI package checks per platform pass. Four new Android
cases exercise secure/multiline input, passive requirements, disabled controls,
step actions and saved-state ownership. Note/progress/counts restore while the
ephemeral password clears. Read step 22 for sources, the corrected disabled-field
selector and remaining keyboard/accessibility/device limits.

Choices/disclosure/detail batch, 2026-10-08: six APIs bring the five component
batches to 39 building blocks. Both apps build; 21 iOS app checks, nineteen
focused Android checks and four UI package checks per platform pass. Native
geometry checks exercise body scrolling and larger text while preserving the
action region. Android additionally executes edited-draft retention and Int
boundary buttons. Read step 21 for actual sources, manual iOS interactions,
the subsequent dark-toolbar build/capture correction and remaining device limits.

Context/navigation/layout batch, 2026-10-08: six APIs bring the four component
batches to 33 building blocks. Both apps build; twenty iOS app checks, fourteen
focused Android checks and four UI package checks per platform pass. New tests
measure native grid reflow under width/text/RTL changes and actual container/media
bounds. Android also exercises child identity, popup dismissal and navigation
restoration. Read step 20 and the native walkthroughs for source links, manual
iOS visual evidence, the corrected double-mirroring finding and remaining limits.

Display/feedback/collection batch, 2026-10-08: six more APIs bring the three
component batches to 27 building blocks. Both apps compile, eighteen existing
iOS app checks, nineteen focused Android checks and four UI package tests per
platform pass. Android includes real pulse/reduced-motion pixels, native editing,
notice lifetime and retained collection selection. Read step 19 and the native
walkthroughs for source links, manual iOS evidence and remaining device limits.

Selection/overlay batch, 2026-10-08: nine more APIs fill the existing mirrored
leaves, bringing these two component batches to 21 building blocks. Controls and
Overlays demonstrate caller-owned selection, date drafts and explicit actions.
Both native apps build; eighteen existing iOS app checks, fourteen focused
Android interaction checks and four token/material tests per platform pass.
Read step 18 and the native walkthroughs for source links, manual iOS evidence
and remaining accessibility/device limits.

Everyday UI batch, 2026-10-08: twelve reusable components and a native Components
gallery extend the family directories. Existing and new component APIs have no
Foundry prefix. Both builds and eleven focused Android checks pass, including
four new gallery checks plus forms/shell regressions. Read step 17 for controlled
state, theme/saved-state retention and native presentation evidence. TalkBack,
all large-font layouts, localization and physical devices remain separate checks.

Latest gallery follow-up, 2026-10-08: Choose photo opens the system single-image
picker; selected URI bytes enter a bounded importer and EXIF-aware decoder.
All seven focused camera/shell checks and Android host regressions pass, and
the APK packages. The tests inject OS selection results but execute real I/O,
decoding and GPU edits. Existing tab vectors are outlined. The initial Sample
photo action below has been replaced. Read the
[gallery walkthrough](modules/project/app/README.md#gallery-input-and-outline-tabs)
and updated picker substrate. Next: how should persisted media access and a
saved asset recipe differ from this transient import?

Camera slice, 2026-10-08: CameraX 1.6.2 supplies the centered Camera
destination, explicit permission, native viewfinder/capture and bounded photo
admission. Image studio and Camera share one native editor. The APK and Android
host regressions pass. All nine focused camera/shell/preview device checks pass
on API36_Test/Android 16, including synthetic capture, actual Mono/Vivid pixels,
tab placement, retained photo/Retake and camera closure. The first Mono check
included the tinted letterbox; the final measurement samples image content.
This slice does not claim a complete unrelated device-suite rerun or physical
camera/denial/interruption coverage. Follow reading step 16. Next: which output
encoding and storage ownership should an export command promise?

The 2026-10-07 bootstrap builds the generated Compose app and the empty
Kotlin/JVM kernel module. The debug APK and two starter unit tests passed.
The generated app illustrates a repository/view-model/screen flow; its raw
Throwable error state is starter code rather than the selected failure policy.

The 2026-10-08 kernel slice implements typed outcomes, immutable failures, and
public projection. Nine kernel host tests passed against shared fixtures,
including callback exception identity and cancellation-exception propagation.
Shared reasoning is in [expected failures and diagnostics](../../../notes/concepts/expected-failures-and-diagnostics.md).
The HTTP slice on 2026-10-08 adds request admission, native transport,
problem/JSON decoding, deadlines, health services and six interactive catalog
scenarios. Both platforms consume the same response/path/retry fixtures.
Twelve HTTP and two service host tests passed; the API 36 APK and existing app unit tests passed. Three instrumented tests passed on API36_Test (Android 16): the existing
starter greeting check and two health checks covering all six scenarios and
replacement of an in-flight request. The AndroidX runner is explicit in the
app build.
Diagnostic observers receive original dependency errors; durable reporting
infrastructure remains separate. Session, persistence, sync and graphics are
future capabilities.

The 2026-10-08 editor investigation in [Gradle bootstrap](substrate/gradle-and-android-bootstrap.md#editor-dependency-imports)
explains why valid build dependencies appeared unresolved in VSCodium. A task-specific
configuration-cache workaround restored the health service's editor classpath;
the editor showed zero problems after reloading, and normal build checks still
stored their configuration cache.

The 2026-10-08 notes/query slice adds a provider-independent NotesService port,
memory and HTTP adapters, an injected ViewModel, and one list renderer. Five
service tests (three new), eight notes ViewModel tests and the two starter tests
passed. Both providers run the same feature-state scenarios. All six device
tests passed on API36_Test/Android 16, including three new notes tests for provider
switching, content/empty/failure, retry, cancel and navigation-entry reopening. HTTP is injected; persistence
and real provider SDK integration remain deferred.

The 2026-10-08 query/UI extraction adds a pure Kotlin query module and an
Android Compose UI library. Two query tests passed against nine shared cases;
eight notes ViewModel and two starter regressions still pass. The modules and
debug APK built. All ten device tests passed on API36_Test/Android 16, including
four new presentation-matrix, callback, public-copy and gallery checks.
The scalar gallery and notes list now consume the same reusable state/UI.

The 2026-10-08 token slice adds V1 light/dark roles, native type/space/shape/motion,
and a scoped theme that adapts Material and observes animator reduction.
Three UI host tests pass for fixtures, contrast, and reduction. Both the library
and app build; eight notes ViewModel and two starter regressions pass. All
fourteen device tests pass on API36_Test/Android 16, adding nested scope, Material
background mapping, fontScale 1/2, minimum touch bounds, and preview/state checks.
The substrate note records the corrected visual-height assertion and pinned
Compose UI 1.10.6 API inspection. Live OS preference changes, TalkBack, exhaustive
large-text layouts, and physical-device behavior remain open.

The same day's [Foundry Studio revision](../../../STYLES.md#design-direction-foundry-studio)
establishes porcelain/graphite/cobalt styling, a four-point rhythm, semibold native
headings, and decelerating motion. Three revised token tests pass, including muted
and inverse contrast pairs; the UI walkthrough explains Material action mapping.
The final full device rerun passes all fourteen checks. The walkthrough records
an earlier transient loading-state timeout, its isolated pass, and emulator
connection delays rather than treating the run history as uniformly clean.

The same day's material slice adds app/local Solid/Glass selection, content/
floating roles, a bounded Compose backdrop host, and transparency reduction.
Four UI unit tests pass and the app builds. New device checks cover material
scope, retained scene state, source placement and updates, and opaque fallback.
All seventeen final device checks pass, including source-edge blur smoothing.
The UI walkthrough records an earlier HTTP timing failure, its isolated pass,
and slow emulator behavior in the final run. External renderer capture,
device performance, and Android system transparency integration remain future work.

The same day's forms/mutation slice adds CreateNote/NoteCreator, mutex-protected
memory and HTTP adapters, MutationState, native controls and feature submission
ownership. Nine service, three query, four UI and seventeen app host tests pass
in `make android-test`; the debug app builds. All twenty-one device checks pass
on API36_Test/Android 16, including four new form/feedback tests for IME, both
providers, refusals/recovery, busy controls and reset. The first run passed
nineteen checks: two new assertions could not find disabled fields through
SetText, an action intentionally absent while disabled. Correcting the matcher
to EditableText produced the complete pass without changing timing thresholds.
TalkBack, exhaustive font/keyboard configurations and physical devices remain open.

The Glass follow-up corrects the form outer panels' role and supplies the missing
Compose backdrop source. All twenty-two device checks pass, including actual
form pixels across style/reduction changes with title and confirmation retained;
the app builds. See [material usage](../../../notes/patterns/material-themes-and-backdrops.md#gotchas)
and the app walkthrough. Theme propagation alone had not established visible usage.

The GPU slice adds core/graphics, OpenGL ES 2 shaders, a native surface and
bounded resolution/time policies. Two host policy tests pass and both build/unit
regression commands pass with the module included. All 25 device checks pass
on API36_Test/Android 16, including actual framebuffer touch/effect differences,
quality dimensions, pause/context recreation/disposal and navigation/reduction.
Read [the graphics walkthrough](modules/project/core/graphics/README.md).
Physical-device performance and accessibility remain unmeasured.

## Product preview follow-up

The product-preview follow-up adds Image studio and Product studio above a
shared GPUPreviewSurface. Four host graphics checks cover admitted values and
owned data; native checks exercise image tools, actual mesh material/camera/pinch,
motion/quality, context recreation and navigation. Read [the preview walkthrough](modules/project/core/graphics/README.md#product-previews),
[asset/gesture mechanics](substrate/gles-preview-assets-and-gestures.md), then
[editable values/resource ownership](../../../notes/patterns/editable-values-and-renderer-resources.md).
Import/export, general models, durable editing and device profiling remain open.
All 29 device checks pass on API36_Test / Android 16, and the final four-check
preview run and Android host regressions pass.

## Questions for the next session

- Why must incremental callbacks read current primitives rather than the last composed camera?
- Why is a successful submission event insufficient for the first PixelCopy read?
- Why must GL handles stay on the render thread even when controls are on main?
- Why does SurfaceView verification use PixelCopy instead of Compose capture?
- What does pausing a frame scheduler fail to prove about queued callbacks?
- Why can valid JSON still be invalid domain data?
- How does a request deadline differ from a screen canceling its work?
- Which layer should renew credentials and decide retries?
- How should a confirmed mutation reach the repository's read observers?
- Why does a disabled text field retain EditableText but lose SetText?
- Why can a Material control's visible height differ from its touch bounds?
- How can editor dependency imports fail while the same source compiles in Gradle?
- Why does a navigation entry need its own ViewModelStoreOwner?
- Why do pure state transformations leave coroutine admission to the feature?
- Why does blurring an overlay's own content fail to provide a sharp glass control?

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

## Placeholder shell reading order

Read [Compose tabs and presentation owners](substrate/compose-tabs-and-presentation-owners.md),
then [the app shell](modules/project/app/README.md#four-tab-placeholder-shell)
and [shared lifetime ownership](../../../notes/patterns/tabs-and-feature-lifetime.md).
The component map is a directory sketch, not completed UI coverage. Next:
when should each destination own a stack, and which feature state should
survive Activity recreation versus closing a presentation?

Shell evidence: native build and host regressions pass. The complete device
run passed 35/36 checks; after correcting an old catalog-entry assumption,
the final eight focused shell/theme checks pass. The app walkthrough records
the correction, saved-state coverage and remaining device limits.

Activity batch verification, 2026-10-09: native consumers build; 27 iOS checks,
four Android activity checks, nineteen existing Android component regressions,
and four UI unit checks per platform pass. Notes validation passes. Module
walkthroughs distinguish scoped reruns, native control/gesture observations and
remaining coverage limits.

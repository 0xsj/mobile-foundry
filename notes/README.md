# Mobile Foundry learning notes

Use this index to study the native foundation between implementation sessions.
Swift, Kotlin, and each backend keep the six Bento note categories; shared mobile
concepts, patterns, and verification techniques live here.

## Start here

1. Read [the notes workflow](../docs/NOTES.md) for classification and evidence.
2. Follow [the Swift reading order](../frontend/swift/notes/README.md) through
   its build configuration, language mechanics, and catalog shell.
3. Follow [the Kotlin reading order](../frontend/kotlin/notes/README.md) through
   its toolchain, UI state types, and starter screen's data flow.
4. Read [expected failures and diagnostics](concepts/expected-failures-and-diagnostics.md),
   then each native kernel walkthrough to compare their common behavior and
   language differences.
5. Follow the new native HTTP and health walkthroughs, then compare
   [transport/service/screen](patterns/transport-service-and-screen.md),
   [deadline ownership](concepts/deadlines-and-owned-cancellation.md), and
   [fixture versus native evidence](techniques/shared-fixtures-and-native-adapters.md).
6. Read the backend setup notes for the profile you are exploring:
   [Go](../backend/go/notes/README.md),
   [Supabase](../backend/supabase/notes/README.md), or
   [Firebase](../backend/firebase/notes/README.md).
7. Follow the native notes feature walkthroughs and [provider seams](patterns/transport-service-and-screen.md#provider-seams-and-query-state)
   to trace screen → state owner → domain port → memory/HTTP adapter.
8. Read [query state and rendering ownership](patterns/query-state-and-rendering.md),
   then each native query/UI walkthrough to compare generic snapshots, native
   content slots, and the feature orchestration that remains outside them.
9. Read [semantic tokens and native themes](patterns/semantic-tokens-and-native-themes.md),
   then the native UI walkthroughs and their framework notes. Compare role/preset
   ownership, nested scope, scalable text, and visual versus touch bounds.
10. Read [material themes and backdrop ownership](patterns/material-themes-and-backdrops.md),
    then the native material walkthroughs to compare native glass, explicit
    Compose source capture, transparency reduction, and rendering evidence.
11. Read [forms and mutation ownership](patterns/forms-and-mutation-ownership.md),
    then the native focus/submit notes and services/query/UI/catalog walkthroughs.
    Trace draft → admitted command → provider → receipt, and compare write
    uncertainty with canceling a read.
12. Read [renderer frame ownership](patterns/renderer-frame-ownership.md), then
    the native graphics walkthroughs and MetalKit/GLSurfaceView substrate notes.
    Trace settings → GPU uniforms → draw → throttled event, and compare native
    resource lifetime with SwiftUI/Compose lifetime.
13. Read [editable values and renderer resources](patterns/editable-values-and-renderer-resources.md),
    then the native graphics/product-preview walkthroughs and texture/mesh/gesture
    notes. Trace decoded asset identity separately from edits and context handles.

14. Read [graphics inputs and finite event time](patterns/graphics-inputs-and-event-time.md), then revisit
    the graphics and catalog walkthroughs. Compare decorative phase, supplied
    progress/data and a finite feature-owned celebration playhead.

15. Read [premultiplied compositing and render passes](patterns/premultiplied-compositing-and-render-passes.md),
    then [graphics profiling and measurement](techniques/graphics-profiling-and-measurement.md).
    Follow the native compositor walkthroughs and [device protocol](../docs/GRAPHICS-PROFILING.md).
16. Read [capture assets and preview lifetime](patterns/capture-assets-and-preview-lifetime.md),
    then the native camera walkthroughs and substrate notes. Compare camera access,
    decoded photograph ownership and independent GPU preview lifetime.

## Current coverage

Everyday UI batch, 2026-10-08: twelve reusable components and a native Components
gallery extend the existing family directories. Components and component-local
types now have descriptive names without the Foundry prefix. Read
[slot and state ownership](patterns/component-slots-and-caller-owned-state.md)
and [usage examples](../docs/blueprints/ui-components.md#using-the-apis), then the
native UI/app walkthroughs. Both builds, eighteen iOS app checks and eleven
focused Android interaction checks pass. Screenshots verify the exercised iOS
light/dark/material layouts; comprehensive accessibility/device coverage remains open.

The 2026-10-08 iOS distribution follow-up fixes missing orientation metadata
without removing iPad support and preserves the user's selected development
team in the generator specification. Read
[generated orientations](../frontend/swift/notes/substrate/swift-package-and-xcode-project-wiring.md#generated-orientation-metadata)
before rebuilding an archive; older archives retain their original metadata.

For the 2026-10-08 Xcode signing investigation, read
[simulator versus device signing](../frontend/swift/notes/substrate/swift-package-and-xcode-project-wiring.md#simulator-and-device-signing-investigation).
The simulator build and Xcode Run passed without a team during that earlier
investigation; the subsequent orientation fix preserves the selected team.

Started 2026-10-07 after native initialization. The current notes explain the
build wiring and existing application scaffolds. Swift package and simulator
builds, Android debug assembly, and two Android starter unit tests passed during
initialization. The walkthroughs state the limits of those checks.

The first kernel slice, completed 2026-10-08, adds typed outcomes, the shared
failure vocabulary, owned validation fields, retry timing, and public projection.
Both platforms consume one canonical fixture set. Their indexes and walkthroughs
record tests, the Swift compiler example, and verification limits.

The HTTP slice, completed 2026-10-08, implements native transports, request
admission, problem decoding, cancellation/deadlines and health domain admission.
Both catalogs expose six injected scenarios without a backend. Notebook indexes
record host, build and runtime evidence. Follow their updated reading orders.

The 2026-10-08 [provider seam review](patterns/transport-service-and-screen.md#provider-seams-and-query-state)
separates implemented transport injection from the planned domain service and
query-state boundaries. The subsequent notes/query slice implements those
boundaries for a read-only list with memory and HTTP adapters. Platform indexes
record shared admission, state-owner and device evidence; managed SDK adapters,
shared caching, writes and persistence remain separate work.

The 2026-10-08 query/UI slice extracts generic value state and native async
presentation, used by notes and a scalar state gallery. Both platforms pass
shared state fixtures and existing feature regressions. Platform indexes record
the ten Android device tests and manual iOS state/action/layout checks.

The 2026-10-08 token slice adds a Bento-style token/preset/theme/component split,
one V1 light/dark preset, native typography and scales, and a Tokens gallery.
Three token tests pass per platform; both apps build and existing state-owner
regressions pass. All fourteen Android device tests pass, including theme scope,
font/touch behavior, and preview controls. Manual iOS checks cover scoped
appearance, reduction, actions, and preserved sample state. Platform notes state
the remaining accessibility, settings-observation, and device limits.

The same day's [Foundry Studio revision](patterns/semantic-tokens-and-native-themes.md#visual-identity-and-semantic-stability)
gives the preset its own porcelain/graphite/cobalt identity, a four-point layout
rhythm, and native heading/motion choices. Start with [the design direction](../STYLES.md#design-direction-foundry-studio),
then revisit the UI walkthroughs for revised values and contrast evidence.
Both native builds, three token tests per platform, and the final fourteen Android
device checks pass. The Kotlin notebook records an earlier loading-state timing
failure and rerun; the Swift notebook records blank screenshot capture during
the revision alongside successful semantic checks.

The same day's material slice adds swappable Solid/Glass treatments without
changing the Foundry Studio palette. Content panels remain opaque; floating
controls use native glass or a Compose backdrop with an explicit fallback.
Four UI unit tests pass per platform, both apps build, and all seventeen final
Android device checks pass. Native walkthroughs
record material scope, retained scene state, Android source/pixel checks, existing
HTTP timing sensitivity, and blank iOS screenshot output. The geometry sample
demonstrates material behavior; it does not establish a GPU renderer integration.

The same day's forms/mutation slice adds a separate NoteCreator port, admitted
CreateNote commands, instance-local memory/HTTP providers, pure mutation phases,
and native text/submit/feedback controls. Nine service and three query tests pass
per platform, thirteen iOS app tests and seventeen Android app host tests pass,
and all twenty-one Android device checks pass after correcting a disabled-field
test matcher. Both apps build. iOS semantic checks and a simulator CLI screenshot
cover the exercised form flow/layout; platform notes record the compiler workaround
and remaining keyboard/accessibility limits. HTTP remains injected, and the read
and write galleries compose independent data instances.

A Glass follow-up corrects the form gallery's always-opaque outer panels.
They now select the floating role and receive a catalog backdrop on both
platforms. Both app builds pass, iOS Solid/Glass screenshots were inspected,
and all twenty-two Android device checks pass, including actual form pixels,
transparency reduction and retained confirmation. See
[material usage gotchas](patterns/material-themes-and-backdrops.md#gotchas).

The GPU slice adds optional native Metal/GL effects and shared bounded-input,
resolution and active-time policies. Both apps build, policy and native Metal
pixel tests pass, and all 25 Android device checks pass, including real surface
pixels, context recreation and navigation disposal. The Swift walkthrough records
the hosted drawable sizing correction and separate simulator evidence.
The graphics exemplar does not establish a complete engine or physical-device
performance. Start with [frame ownership](patterns/renderer-frame-ownership.md).

The next graphics slice adds **Image studio** and **Product studio**, sharing a
native preview adapter with owned opaque pixels/mesh data and bounded editing
values. Native Metal tests verify orientation/filter/viewport/material/camera
pixels. Hosted iOS checks verify layout/static redraw/removal. Android device
checks exercise the external surface, gestures and context lifetime. Read
[editable values/resource ownership](patterns/editable-values-and-renderer-resources.md)
and the native walkthroughs for the observed corrections and final test evidence.
Import/export, general models, durable edits and physical-device budgets remain open.

Identity, account, persistence, sync, broader UI controls, and richer graphics
remain planned capabilities.
Their learning notes will arrive with actual implementation or investigation.

Backend profiles were scaffolded 2026-10-08. Go has reserved module layers and
no `go.mod`; the provider notebooks record local stack configuration and the
checks performed. Provider setup alone does not establish native session,
repository, or synchronization behavior.

## Selection and overlay batch

The 2026-10-08 follow-up adds six selection controls and three native overlay
patterns on both platforms, with Controls and Overlays gallery families. Read
[committed values and transient interaction](patterns/component-slots-and-caller-owned-state.md#committed-values-and-transient-interaction--2026-10-08),
then reading step 18 in each platform notebook. Committed selection belongs to
the app; date drafts and menu expansion are temporary component mechanics.
The native walkthroughs record actual checks and date/time-zone representation
differences. Existing details/removal examples now reuse the shared wrappers. Both native
apps build; eighteen existing iOS app checks, fourteen focused Android checks
and four token/material checks per platform pass. Native interaction and
screenshot observations are separate from those automated counts.

## Display feedback and collection batch

The 2026-10-08 third batch adds Avatar, StatCard, Skeleton, ToastBanner, FieldGroup
and CollectionToolbar to both native UI trees. Display, Feedback and Collections
exercise passive identities, loading reduction, explicit recovery and visible
selection scope; Controls adds grouped editing. Read
[collection projections and feedback lifetime](patterns/collection-projections-and-feedback-lifetime.md),
then reading step 19 in each native notebook. Both apps compile; eighteen existing
iOS app checks, nineteen focused Android checks and four UI package checks per
platform pass. The native walkthroughs separate runtime/pixel/manual evidence
from remaining accessibility and hardware questions.

## Shared findings

| Directory | Responsibility |
| --- | --- |
| `concepts/` | Mobile and systems distinctions that apply to both platforms |
| `patterns/` | Approaches shared by the native implementations |
| `techniques/` | Methods for investigating or verifying shared behavior |

Read [expected failures and private diagnostics](concepts/expected-failures-and-diagnostics.md)
for the first shared finding, then [request ownership](concepts/deadlines-and-owned-cancellation.md),
[service boundaries](patterns/transport-service-and-screen.md), and
[parity verification](techniques/shared-fixtures-and-native-adapters.md).
The [theme pattern](patterns/semantic-tokens-and-native-themes.md) explains the
new token ownership and native adaptation decisions.
Add a linked entry and useful reading position when a shared note is written. Source contracts
remain in [contracts](../contracts/README.md), and accepted boundaries remain in
[Architecture](../ARCHITECTURE.md).

## Questions for the next slice

- Which additional controls should exercise keyboard, focus and validation behavior?
- Which field-state roles and contrast pairs are needed beyond decorative separators?
- Which second field or form would justify reusable cross-field validation?
- Which repository should coordinate confirmed writes with observable reads?
- What storage and migration contract is needed before drafts survive process death?
- How should a feature own repeat/retry work and suppress obsolete results?
- Which second asynchronous feature would justify extracting request orchestration?
- What evidence is required before a connected write can be safely replayed?
- How should an external renderer supply a backdrop while retaining frame ownership?
- Which asset cache and recipe version are needed before preview edits become durable?
- Why can incremental native gestures lose deltas between compositions?

Run `make notes-check` after changing learning notes. Complete code examples
also need the execution commands described in their notes.

## GPU use-case expansion

The same day's expansion adds five procedural consumers: Flow, Material, Liquid,
Particles and Field. Read [graphics inputs and event time](patterns/graphics-inputs-and-event-time.md).
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

The 2026-10-08 asset slice adds a generic cobalt ribbon icon to the iOS catalog.
Read [asset ownership and provenance](../assets/README.md#mobile-foundry-app-icon)
and [native packaging](../frontend/swift/notes/substrate/swift-package-and-xcode-project-wiring.md#generated-app-icon).
The original and opaque 1024-square derivative are retained with exact prompt
and hashes. The simulator build and asset checks pass; release remains pending
other pre-TestFlight slices.

## Placeholder navigation shell

The 2026-10-08 shell slice reserves mirrored component leaves for visualization
and adds Home, Library, Studio and Account. Read
[tabs and feature lifetime](patterns/tabs-and-feature-lifetime.md), then the
native app walkthroughs and framework notes. Studio opens the existing catalog;
Account controls the shared material choice. The
[component map](../docs/COMPONENTS.md) lists intended contents and distinguishes
reserved leaves from implemented controls. Feature stacks, deep links and
identity remain future slices.

Both native builds, sixteen iOS app/layout checks and Android host regressions
pass. Manual iOS navigation and both Home screenshots were inspected. Android's
complete run passed 35/36 checks; after updating an old theme test for the shell
entry point, the final eight shell/theme checks pass. Native walkthroughs record
saved-state coverage and the remaining device/accessibility limits.

## Middle Camera destination

The 2026-10-08 camera slice extends the shell to Home, Library, Camera, Studio,
Account. Camera has explicit permission, native live preview/still capture,
front/back selection, a grid and Retake. Captured pixels and the bundled sample
feed the same GPU editor as Image studio. Natural/Mono/Vivid/Soft reuse existing
adjustments; exposure, saturation, vignette, comparison, pan and zoom remain
available. Read [capture ownership](patterns/capture-assets-and-preview-lifetime.md),
then each native walkthrough and framework note from reading step 16.

Both native apps build; the unsigned iOS device branch also compiles. Eighteen
iOS app checks, Android host regressions and the focused nine-check Android
camera/shell/preview suite pass. Android executes a synthetic camera capture,
real GPU filter pixels and owned camera closure. Manual iOS sample checks cover
filter selection, retained photo across tabs, Retake and actual grayscale/layout
screenshots. Physical capture quality/orientation, interruptions and device
performance remain unmeasured. Saving/export and durable photo recipes are
separate slices. Next: which output resolution, encoding and asset lifetime
should a saved edit promise?

## Gallery and outline follow-up

The 2026-10-08 follow-up replaces Sample photo with native library selection.
Both importers feed the existing bounded CPU-photo/editor boundary. Capture
pauses during picking/loading; cancellation leaves the current screen usable.
Tab icons stay outlined, with native selected highlights and accessible states.
Read the revised [capture pattern](patterns/capture-assets-and-preview-lifetime.md#selected-library-input--2026-10-08)
and the native gallery walkthroughs from reading step 16.

Eighteen iOS app checks, seven focused Android camera/shell checks and Android
host regressions pass; both apps package. Real iOS system-picker selection was
observed with a seeded photograph. Android tests inject picker results and then
exercise actual URI I/O, EXIF-aware decoding, cancellation/failure UI and GPU
filters. Cloud providers, broad accessibility and device memory/performance
remain open. The preceding sample checks record the original camera slice.

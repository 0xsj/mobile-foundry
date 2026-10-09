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

The twenty-third UI batch adds Verification (108 building blocks, 29 families).
Start with [challenge drafts and attempt identity](patterns/challenge-drafts-and-attempt-identity.md),
then native reading step 39 and the Verification gallery walkthroughs. Both
consumers build; 61 iOS app checks, four focused Android Verification UI checks
and nine Swift/eight Kotlin UI package checks pass (2026-10-09). The local fixture
uses a manual clock and authenticates no account; real delivery/challenge services
and native Autofill observations remain separate work.

The twenty-second UI batch adds Playback (106 building blocks, 28 families).
Start with [media timeline and transport admission](patterns/media-timeline-and-transport-admission.md),
then native reading step 38 and the Playback gallery walkthroughs. Both consumers
build; 59 iOS app checks, four focused Android Playback UI checks and seven Swift/
six Kotlin UI package checks pass (2026-10-09). The local timeline advances
manually and plays no audio; native engine and OS session work remain separate.

The twenty-first UI batch adds Sharing (104 building blocks, 27 families). Start
at step 37 in each native notebook and
[membership identity and confirmed revisions](patterns/membership-identity-and-confirmed-revisions.md).
Native member/link compositions keep role policy and clipboard effects in the
feature, with protected owners and revision-bound local confirmations.

The twentieth UI batch adds Files (102 building blocks, 26 families). Start
at step 36 in each native notebook and
[tree projection and retained selection](patterns/tree-projection-and-retained-selection.md).
Rows receive flattened values while the fixture owner manages expansion, ancestor
search, favorites and selected identity.

The nineteenth UI batch adds Plans (100 building blocks, 25 families). Start
at step 35 in each native notebook and
[plan choice and applied allowance](patterns/plan-choice-and-applied-allowance.md).
Native feature/plan/usage APIs compose a local plan picker; reviewed application
changes current allowance without silently resetting usage.

The eighteenth UI batch adds Notifications (97 building blocks, 24 families).
Start at step 34 in each native notebook and
[inbox projection/read identity](patterns/inbox-projection-and-read-identity.md).
The local inbox composes native count/row APIs with existing sections, menus and
alerts; stable read/archive state stays separate from filtering and detail flags.

The seventeenth UI batch adds Commerce (95 building blocks, 23 families). Start
at step 33 in each native notebook and
[price copy and committed cart values](patterns/price-copy-and-committed-cart-values.md).
Native products, code input and order summary compose a local cart with explicit
admission and retained reviews; pricing services and purchases remain future work.

The sixteenth UI batch adds Discovery (91 building blocks, 22 families). Start
at reading step 32 in each native notebook and
[search projections and filter drafts](patterns/search-projection-and-filter-drafts.md).
The preview explores literal highlights, suggestions/history, independent
bookmarks and explicit applied/draft filters without a search service.

The eleventh UI batch adds Insights and small charts, for 74 building blocks in
17 families. Start at reading step 27 in each native notebook and
[chart meaning/scales](patterns/chart-meaning-and-scales.md).

The tenth UI batch adds selection, tokens and row editing, for 68 building blocks
in 16 families. Start at reading step 26 in each native notebook and
[selection identity/undo](patterns/selection-identity-and-undo.md).

The ninth UI batch adds communication/attachments, for 63 building blocks in 15
families. Start at reading step 25 in each native notebook and
[composer draft/transfer ownership](patterns/composer-drafts-and-transfer-ownership.md).

The eighth UI batch adds media browsing/actions, for 57 building blocks in 14
families. Start at reading step 24 in each native notebook and
[media selection/artwork ownership](patterns/media-selection-and-passive-artwork.md).

The seventh UI batch adds activity/feed compositions, for 51 building blocks.
Start at reading step 23 in each native notebook and
[refresh/page ownership](patterns/refresh-and-pagination-ownership.md).

The sixth UI batch adds rich input, passive readiness and account/onboarding
layouts, for 45 building blocks. Start at reading step 22 in each native notebook;
the [batch summary](#rich-input-and-journey-batch) links reasoning and evidence.

The fifth UI component batch adds choices, disclosure and detail compositions,
for 39 building blocks. Start at reading step 21 in the native notebooks; the
[batch summary](#choices-disclosure-and-detail-batch) links the shared reasoning
and records exercised behavior.

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

## Context navigation and layout batch

The fourth UI batch, 2026-10-08, adds NavLink, PopoverPanel, HelpTooltip,
ContentContainer, AdaptiveGrid and MediaFrame on both platforms, for 33 building
blocks. Context and Layout exercise caller-owned presentation/routes and small
responsive compositions. Read [geometry and contextual navigation](patterns/component-slots-and-caller-owned-state.md#geometry-and-contextual-navigation--2026-10-08),
then step 20 in each native notebook for actual source/check links. Both apps
build; twenty iOS app checks, fourteen focused Android checks and four UI package
checks per platform pass. Native walkthroughs separate geometry, interaction and remaining device
limits. Layouts do not add routing, durable storage or a lazy data pipeline.

## Choices disclosure and detail batch

The fifth UI batch, 2026-10-08, adds ChoiceChip, ValueStepper, DisclosureSection,
KeyValueRow, ActionBar and DetailShell on both platforms, for 39 building blocks.
Details and a separate delivery preview exercise caller-owned choices/drafts and
a bounded screen with scrolling content and persistent actions. Read
[collapsed drafts and detail viewports](patterns/component-slots-and-caller-owned-state.md#collapsed-drafts-and-detail-viewports--2026-10-08),
then step 21 in each native notebook for source, native mechanics and arithmetic.

Both apps build; 21 iOS app checks, nineteen focused Android checks and four UI
package checks per platform pass. New checks measure actual scroll/footer and
larger-text geometry; Android additionally executes edited-note retention,
saved state, local Apply/Reset and extreme-Int button changes. iOS manual checks
cover ordinary endpoints, disclosure and pinned actions. An inspected fresh
build/capture verifies the subsequent app-only native dark-toolbar correction.
The walkthroughs distinguish this evidence from broad accessibility, keyboard
and hardware coverage. The example has no export or persistence implementation.

## Rich input and journey batch

The sixth UI batch, 2026-10-08/09, adds PasswordField, MultilineField,
ValidationChecklist, StepIndicator, OnboardingPage and AuthShell, for 45 building
blocks. Journeys exercises native input, passive readiness and caller-owned step
actions through account and three-step onboarding previews. Read
[input drafts and journey steps](patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08),
then step 22 in each native notebook for source and framework mechanics.

Both apps build; 22 iOS app checks, nineteen focused Android component checks and
four UI package checks per platform pass. New iOS evidence inspects native secure
input and measures growing/capped multiline geometry and larger text. Android
executes real editing, readiness, disabled inputs, progression and saved-state
restoration, including clearing the ephemeral password while retaining the note.
Manual iOS input/navigation and CLI screenshots were inspected; hardware keyboard
behavior and corrected test assumptions are documented beside their limits.
The previews change local counts and drafts, without implementing sessions or
durable profile/credential storage.

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

## Activity and paged collections batch

Added 2026-10-09: SectionHeader, AvatarGroup, TimelineItem, ExpandableText,
RefreshContainer and LoadMoreFooter on both platforms. Activity opens a native
List/LazyColumn fixture with explicit load/retry/exhausted states and refresh.
Read [shared reasoning](patterns/refresh-and-pagination-ownership.md),
[Swift mechanics](../frontend/swift/notes/substrate/swiftui-refresh-and-lazy-activity.md)
and [Compose mechanics](../frontend/kotlin/notes/substrate/compose-refresh-and-lazy-activity.md).
Native app walkthroughs record execution evidence and separate unsupported
claims, including remote paging, durable storage and physical-device behavior.

Activity batch verification, 2026-10-09: native consumers build; 27 iOS checks,
four Android activity checks, nineteen existing Android component regressions,
and four UI unit checks per platform pass. Notes validation passes. Module
walkthroughs distinguish scoped reruns, native control/gesture observations and
remaining coverage limits.

## Media browsing and actions batch

Added 2026-10-09: IconAction, RatingField, PageIndicator, Carousel, MediaTile and
MediaOverlay on both platforms. The Media gallery and separate preview share
selection, ratings, favorite IDs and a local use count. Read
[shared ownership](patterns/media-selection-and-passive-artwork.md),
[Swift native mechanics](../frontend/swift/notes/substrate/swiftui-media-paging-and-overlays.md)
and [Compose native mechanics](../frontend/kotlin/notes/substrate/compose-media-paging-and-overlays.md).
Both consumers build. All 29 iOS app checks, four final Android media checks,
fourteen existing Android component/activity regressions and four UI unit checks
per platform pass. Notes validation passes.
Module walkthroughs distinguish native control/gesture evidence, manual visual
observations and remaining coverage limits.

## Communication and attachments batch

Added 2026-10-09: ConversationRow, MessageBubble, MessageComposer, AttachmentRow,
TransferStatus and TypingIndicator on both platforms. Design room composes a
local multiline draft, attachment recovery, independent inspection and a pinned
composer. Start with [draft and operation ownership](patterns/composer-drafts-and-transfer-ownership.md),
[Swift keyboard placement](../frontend/swift/notes/substrate/swiftui-composer-and-safe-area.md)
and [Compose state/insets](../frontend/kotlin/notes/substrate/compose-composer-and-ime.md).
Both consumers build. All 32 iOS app checks, four final Android communication
checks, thirteen existing component/media regressions and four UI unit checks
per platform pass. Native walkthroughs separate initial harness corrections,
manual iOS keyboard/recovery/theme observations and remaining device/service limits.

## Selection, tokens and row editing batch

Added 2026-10-09: WrapLayout, RemovableChip, TokenField, SelectionRow and
SwipeActionRow (with SwipeAction) compose the Editing library preview. Read
[identity/undo](patterns/selection-identity-and-undo.md), then
[Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#editing-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#editing-gallery)
for source, state ownership and actual evidence. Both apps build; 35 iOS checks,
five final Android editing checks, thirteen existing Android regressions and four
UI unit checks per platform pass. Native wrapping/RTL and Android swipe/restoration
are exercised; manual iOS action/menu/undo and two themes are recorded separately.
No persistent library, complete accessibility or physical-device audit is claimed.
Next: add an actual collection command port with revision-aware undo admission.

## Insights and small charts batch

Added 2026-10-09: TrendBadge, LegendItem, Sparkline, BarChart, ProgressRing and
ChartPanel compose the Insights dashboard. Read [chart meaning/scales](patterns/chart-meaning-and-scales.md),
then [Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#insights-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#insights-gallery)
for source and evidence. Both consumers build; 37 iOS app checks, four final
Android Insights checks, five editing regressions and six UI unit checks per
platform pass. Native numerical bounds, Android pixels/semantics/restoration and
Swift hosted larger-text geometry are exercised. Manual iOS drawing/goal checks
and the post-fix automation limitation are recorded separately. No complete
accessibility, real analytics, dense time series or device budget is claimed.
Next: define an admitted metrics port and a shared time/axis policy when needed.

## Dates and agendas batch

Added 2026-10-09: TimeField/ClockTime, DateRangeField, DayStrip/DayOption and
AgendaRow compose a local Scheduling planner (78 building blocks, 18 families).
Start with [calendar dates and clock readings](patterns/calendar-dates-and-clock-readings.md),
then [Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#scheduling-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#scheduling-gallery)
for source, checks and limits. Both apps build; 39 iOS app checks, five Android
scheduling checks, one date-picker regression and seven Swift/six Kotlin UI checks pass. Native modal edits,
command guards, larger-text geometry and Android restoration are exercised.
No calendar access, recurrence, reminder, real availability or durable booking
is implemented. Next: define calendar/zone conversion and a revision-aware
scheduling command at the feature/service boundary.

## Adaptive workspaces batch

Added 2026-10-09: DestinationRail/RailDestination, BreadcrumbTrail/BreadcrumbItem
and SplitPane/PaneMode compose the Workspace browser (81 building blocks,
19 families). Read [adaptive layout and navigation state](patterns/adaptive-layout-and-navigation-state.md),
then [Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#workspace-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#workspace-gallery)
for actual sources and limits.
Verification, 2026-10-09: both native consumers build; all 41 iOS app
checks, five focused Android Workspace UI checks and seven Swift/six
Kotlin UI package checks pass. `make notes-check` validates links and
example labels, not native behavior.
Native controls, synthetic wide/narrow geometry, RTL/larger text and Android
restoration are exercised. No root router, deep links, durable projects, complete
accessibility or physical tablet/foldable audit is established. Next: decide
route serialization and focus restoration for a real workspace.

## Tables and pagination batch

Added 2026-10-09: TableSortHeader/TableSortOrder, DataTable/DataTableColumn and
PaginationBar compose the local Project ledger (84 building blocks, 20 families).
Read [sort and page ownership](patterns/table-sorting-and-page-ownership.md), then
[Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#tables-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#tables-gallery)
for sources, actual checks and limits.
Verification, 2026-10-09: both native consumers build; all 43 iOS app checks,
four focused Android Tables UI checks, seven Swift and six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
Native alignment, scroll movement, larger text/RTL and Android restoration are
exercised. No virtualization, full accessibility or real ordered-page service is
established. Next: decide numbered-page/cursor semantics and stale response admission.

## Commerce batch

Added 2026-10-09: PriceLabel, ProductRow, OrderSummary and InlineActionField
compose Cart preview (95 building blocks, 23 families). Read
[price/command ownership](patterns/price-copy-and-committed-cart-values.md), then
step 33 in each native notebook for source and adaptive field mechanics.
Draft codes, applied discounts, derived current totals and review snapshots have
separate ownership. Both consumers build; 49 iOS app checks, four focused Android
Commerce UI and seven Swift/six Kotlin UI package checks pass. Notes checks
validate links and labels. No authoritative quote, reservation, tax/currency
domain, purchase operation or full assistive-technology/device audit is established.

## Discovery batch

Added 2026-10-09: HighlightedText/HighlightSegment, SearchSuggestionRow and
SearchResultRow compose Search workspace (91 building blocks, 22 families).
Read [search projection and draft ownership](patterns/search-projection-and-filter-drafts.md),
then step 32 in each platform notebook for source and native text mechanics.
Applied values, saved IDs and recent queries stay feature-owned; modal drafts
are discarded. Literal text styling introduces no parsing or search engine.
Both consumers build; 47 iOS app checks, four focused Android Discovery UI checks
and seven Swift/six Kotlin UI package checks pass. Notes checks validate links
and labels. No service, durable history, multilingual ranking or full
assistive-technology/physical-device audit is established.

## Accounts and access batch

Added 2026-10-09: ProfileHeader, AccountSwitcher/AccountOption, SessionRow and
PermissionCard compose the Account center (88 building blocks, 21 families).
Read [context and device capabilities](patterns/account-context-and-device-capabilities.md),
then [Swift](../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#account-gallery)
and [Kotlin](../frontend/kotlin/notes/modules/project/app/README.md#account-gallery)
for actual source, native evidence and limits.
Verification, 2026-10-09: both native consumers build; all 45 iOS app checks,
four focused Android Account UI checks and seven Swift/six Kotlin UI package
checks pass. `make notes-check` validates links/example labels, not execution.
Native menu selection/disabled state, independent actions, account-qualified
removal, cancelled confirmations, larger text/RTL and Android restoration are
exercised. Actual credentials, session services, OS access and full accessibility
audits remain separate. Next: define principal/workspace scope before connecting
real guarded session commands or platform capability adapters.

## Notifications batch

Added 2026-10-09: CountBadge and NotificationRow compose Inbox preview
(97 building blocks, 24 families). Read
[inbox projection/read identity](patterns/inbox-projection-and-read-identity.md)
and step 34 in each native notebook for source and native mechanics. Existing
section/menu/alert components supply grouping, archive and undo presentation.
Both consumers build; 51 iOS app cases, four focused Android inbox cases and
seven Swift/six Kotlin UI package cases pass. Android checks cover restoration
and retained earlier family state. Notes checks validate links/labels only.
No push integration, synchronized receipts or full assistive/device audit is
established. Next: define account scope, offline receipts and undo conflicts.

## Plans batch

Added 2026-10-09: FeatureRow, PlanCard and UsageMeter compose Plans preview
(100 building blocks, 25 families). Start with
[plan choice and applied allowance](patterns/plan-choice-and-applied-allowance.md)
and reading step 35 in each native notebook. Selected, reviewed and current
choices stay separate; applying a local plan preserves usage and can expose an
exceeded allowance. Both consumers build; 53 iOS app cases, four focused Android
Plans cases and seven Swift/six Kotlin UI package cases pass. Notes checks validate
links/labels, not execution. No store billing, receipt verification or full
assistive/device audit is established. Next: actual entitlement and usage-period scope.

## Files batch

Added 2026-10-09: FileTypeMark and TreeRow/TreeDisclosure compose Files preview
(102 building blocks, 26 families). Start with
[tree projection and retained selection](patterns/tree-projection-and-retained-selection.md),
then native reading step 36 and the Files gallery walkthroughs.

The fixture separates saved expansion, matching-plus-ancestor search projection,
retained selected identity, favorites and transient inspector presence. Both apps
build; 55 iOS app checks, four focused Android Files cases and seven Swift/six
Kotlin UI package cases pass. Native cases exercise bounded extreme-depth
large-text/RTL targets, independent actions, restoration, disabled/empty admission
and retained earlier family state. Notes checks validate links/labels, not execution.
Real filesystem/provider loading, permissions and full assistive traversal remain
separate work.

## Sharing batch

Added 2026-10-09: MemberRow and ShareLinkCard compose Sharing preview
(104 building blocks, 27 families). Start with
[membership identity and confirmed revisions](patterns/membership-identity-and-confirmed-revisions.md),
then native reading step 37 and the Sharing gallery walkthroughs.

The fixture separates identity/roles from native composition and guards invitations,
protected owners, revision-bound removals and explicit copy admission. Both apps
build; 57 iOS app cases, four focused Android Sharing cases and seven Swift/six
Kotlin UI package cases pass. Checks cover independent native actions, narrow
large-text/RTL layouts, restoration, invalidation and retained prior family state.
Notes checks validate links/labels, not execution. No real invitations, access
tokens or server authorization are provided; native selection/clipboard UI and
full assistive traversal need separate observations.

## Playback batch

The twenty-second UI batch adds PlaybackControls and NowPlayingCard
(106 building blocks, 28 families). Start with
[media timeline and transport admission](patterns/media-timeline-and-transport-admission.md),
then native reading step 38 and the Playback gallery walkthroughs.
Both consumers build; 59 iOS app cases, four focused Android Playback UI checks
and seven Swift/six Kotlin UI package checks pass (2026-10-09).
They cover bounded transport/seek/replay/repeat, independent favorites, narrow
large-text/RTL geometry and Android saved-state/routes with earlier cart retention.
The slider updates local state and Advance is manual: no audio, automatic clock,
real player recovery or device playback performance is established. Notes checks
validate links, module paths and example labels rather than executing examples.

## Verification batch

The twenty-third UI batch adds OneTimeCodeField/CodeFormat and VerificationCard
(108 building blocks, 29 families). Start with
[challenge drafts and attempt identity](patterns/challenge-drafts-and-attempt-identity.md),
then native reading step 39 and the Verification gallery walkthroughs.
Both consumers build; 61 iOS app cases, four focused Android Verification UI checks
and nine Swift/eight Kotlin UI package checks pass (2026-10-09).
They cover code admission/native hints, local request identity/expiry, narrow
large-text/RTL geometry and Android recreation/routes with earlier cart retention.
Android code/pending/error/success presentation is transient while nonsecret
choices/times/counters restore. No code delivery, automatic deadline, OS Autofill
suggestion, auth session or full assistive traversal is established. Notes checks
validate links, module paths and example labels rather than executing examples.

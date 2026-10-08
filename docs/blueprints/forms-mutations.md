# Forms and mutations construction specification

## Status and scope

Implemented and verified, 2026-10-08. Native Create note workflow
through shared behavior, independent Swift/Kotlin code, and injectable providers.
The notes domain is an exemplar for reusable forms and write presentation.

## Exemplar workflow

Enter a title, validate on blur/submit, create once, retain input after refusal,
and explicitly start a new draft after confirmation. Exercise memory and injected
HTTP with the same screen. Persistence, backend deployment, authentication,
idempotency, rollback, reconciliation, and generic request execution are deferred.

## Source documents and authority

[Architecture](../../ARCHITECTURE.md), [Styles](../../STYLES.md),
[forms/mutation contract](../../contracts/behavior/forms-mutations.md),
[kernel](../../contracts/behavior/kernel.md), [HTTP](../../contracts/behavior/http.md),
and [notes reads](../../contracts/behavior/notes-query.md) are established inputs.
Native framework adaptation is established by the current packages/Gradle build.
Persistence is not applicable. The contract owns behavior; this file assigns files.

## Nouns, states, and invariants

CreateNote owns an admitted title. NoteCreator owns the narrow write seam; Note
remains the returned domain value. MutationState owns a pure phase/receipt/failure.
Feature state owns draft/touched/error, generation and execution. The UI owns
labels, native controls and callbacks. See the contract for exact admission,
state, reduction, refusal and uncertainty rules.

## Workflow and side-effect ordering

Validate synchronously, reject invalid or busy/completed submission, then mark
Submitting before dispatch. Invoke exactly one provider. Admit only the current,
non-canceled generation. Preserve draft on failure and keep success receipt until
explicit reset. Screen exit invalidates admission before cancellation. Never
retry a write automatically. The memory instance commits an append atomically;
HTTP commit status is external and may remain unknown after a missing response.

## Expected file tree and file specifications

All listed production/test files are required. Existing navigation, package/app
notes, indexes, and build targets are updated rather than duplicated. Relative
paths below start inside each platform's existing source/test root.

| Swift / Kotlin file | Owner, symbols, dependencies, refusals and verification |
| --- | --- |
| services `CreateNote.swift` / `CreateNote.kt` | CreateNote factory and NoteCreator port; kernel/Note only; invalid-title fields before side effects; shared command cases. |
| services `InMemoryNoteCreator.swift` / `InMemoryNoteCreator.kt` | Actor/mutex instance state, create and list; admitted commands only; cancellation before append, owned snapshots; concurrent unique creates and cancellation checks. |
| services `HTTPNoteCreator.swift` / `HTTPNoteCreator.kt` | POST wire mapping and note admission; HTTP client dependency only; pass failures/defects, classify invalid entity with metadata; shared response/body checks. |
| query `MutationState.swift` / `MutationState.kt` | Pure phase/value/failure projections and transitions; kernel only; no execution or retained read snapshot; shared state matrix. |
| UI `Components/Forms/TextField/LabeledTextField.swift` / `components/forms/textfield/LabeledTextField.kt` | Label/help/error and native editing; caller bindings/callbacks/focus, theme tokens; no validation/service policy; field and keyboard device checks. |
| UI `Components/Forms/SubmitButton/SubmitButton.swift` / `components/forms/submitbutton/SubmitButton.kt` | Native progress/disabled action; copy/busy/enabled/callback; no task ownership; exact callback and disabled semantics. |
| UI `Components/Feedback/Mutation/MutationFeedback.swift` / `components/feedback/mutation/MutationFeedback.kt` | Mutation state, progress copy and success slot; public failure projection; no retry callback; public-copy and render matrix checks. |
| app `Forms/CreateNoteStore.swift` / `ui/forms/CreateNoteViewModel.kt` | Draft, touched/client/server field mapping, submit/use/reset/stop and generation; NoteCreator injected, diagnostic callback; guard duplicate writes and obsolete publication; state-owner tests. |
| app `Forms/CreateNoteScreen.swift` / `ui/forms/CreateNoteScreen.kt` | Feature screen over reusable controls; caller feature state/actions; native focus/IME and missing-confirmation copy; catalog/device checks. |
| app `Forms/FormsCatalogView.swift` / `ui/forms/FormsCatalogScreen.kt` | Catalog selection and feature lifetime; composition factory; refuse source changes while busy; navigation/provider scenarios. |
| app `Composition/CreateNoteComposition.swift` / `composition/CreateNoteComposition.kt` | Providers/scenarios and injected transport/decorators; no reusable fake errors in core; same domain path for memory/HTTP; provider parity tests. |
| services tests `CreateNoteTests.swift` / `CreateNoteTest.kt` | Command admission, request shape, response classification, snapshots, cancellation and defect propagation. |
| query tests `MutationStateTests.swift` / `MutationStateTest.kt` | Shared matrix, receipt dropping, explicit reset and pure settlement. |
| app tests `CreateNoteStoreTests.swift` / `CreateNoteViewModelTest.kt` | Validation ordering, draft/errors, duplicates, service replacement, reset, cancellation, late success/defect, diagnostic identity. |
| Android device `ui/forms/FormsCatalogScreenTest.kt` | Actual controls, keyboard submit, provider parity, server errors, disabled busy action, reset and public feedback. |

Required shared fixtures: `contracts/fixtures/notes/create-titles.json`,
`contracts/fixtures/notes/create-responses.json`, and
`contracts/fixtures/query/mutations.json`. Runtime never loads checkout fixtures.
Required learning handoff: shared mutation/form pattern, native language/framework
mechanics, mirrored module walkthrough updates and all three reading indexes.

## Cross-file dependency map

Catalog composition → feature state owner → NoteCreator → memory or HTTP.
Feature renderer → reusable controls/feedback → theme and MutationState.
Query and UI modules cannot import services or catalog code. The read port remains
source-compatible. No new package/module/dependency is required.

## Transport and persistence mapping

POST notes carries the admitted title and expects a note envelope. Existing
HTTP request options and problem decoding are reused. No schema migration or
disk state exists. Memory storage is instance-local, and injected wire scenarios
cannot be represented as evidence of a deployed backend.

## Verification plan

Run query, service and UI package/unit checks, both app builds, iOS state-owner
tests and Android host/device suites. Exercise iOS focus/submit/errors/reset and
provider swapping through native UI. Run notes-check and diff checks. Record
actual results, tool limitations and any timing failures without changing unrelated
test thresholds. A compile/build result alone is not rendering evidence.

## Construction order

Contract/fixtures → admitted command and providers → pure mutation values → native
controls → feature state owner → composition/navigation → host and device checks
→ learning handoff and completeness review.

## Open decisions and unknowns

Future server idempotency, reconciliation, account scope, persistent drafts and
additional field types need separate contracts. Screen-reader quality, all
keyboard/text-size configurations and device behavior require later measurement.

## Deviation record

Glass follow-up, 2026-10-08: the initial always-opaque form panels made the
app Glass selection invisible on this screen. The user requested correction.
The forms contract now selects floating outer panels with a catalog-owned
decorative backdrop. Required changes stay in FormsCatalogView/Screen,
CreateNoteScreen and FormsCatalogScreenTest; private background rendering stays
in the catalog. CatalogView also updates the home switch hint to name both
consumers. No new module or shared surface API is needed. Verify actual
Solid/Glass pixels, reduction, retained drafts, both builds and existing form checks.

The original slice had no boundary, dependency or file-tree deviations. The
material usage correction is recorded above. Swift 6.2.3 crashed in IR
generation for the field Binding setter's actor method reference; an explicit
closure preserves behavior and compiles. Two initial Android device assertions
incorrectly selected disabled fields through SetText; the EditableText matcher
fix passes without changing thresholds or production behavior. CUA screenshots
were blank, but simctl capture provided a viewable screenshot for layout review.

## Verification record

2026-10-08: nine Swift service and three Swift query package tests pass; Android
host suites pass, including nine service, three query, four UI and seventeen app
tests. `make ios-test` builds the app/UI and passes thirteen app tests on iPhone
17 Pro/iOS 26.2. Both final app builds pass. The complete Android device rerun
passes twenty-one tests (four new) on API36_Test/Android 16 in 50 seconds, after
the nineteen-of-twenty-one matcher failure described above.

iOS native interactions cover validation feedback/focus, Return submission, confirmed
disabled state/reset, provider swap with retained draft, server title error and
HTTP recovery and fresh navigation reopening under Glass. The populated refusal
form's CLI screenshot was inspected for
layout. Shared/platform indexes and eight module walkthroughs link real source
and tests; notes-check and diff checks pass. No deployed backend, persistent
drafts, idempotent replay, comprehensive screen-reader/large-text audit or physical
device evidence is claimed.

Glass follow-up verification: both app builds pass and all twenty-two Android
device tests pass in 61 seconds. The new form pixel check distinguishes Glass
from Solid, verifies opaque transparency reduction, and retains title/receipt.
iOS Solid/Glass CLI captures were visually inspected; explicit width constraints
keep outer panel layout independent of material. No shared renderer API changed.

## Completion criteria

Both native catalogs run the same form behavior through both providers; shared
fixtures and native checks pass; lifecycle/uncertainty rules are explicit; notes
link actual source, explain new mechanics and record verification limits.

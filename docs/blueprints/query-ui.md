# Query state and async UI construction specification

## Scope and authority

The [query/UI contract](../../contracts/behavior/query-ui.md) owns this slice.
The [notes contract](../../contracts/behavior/notes-query.md) continues to own
service behavior and feature lifetime. Extract shared value state and native
rendering, keeping orchestration in the existing notes state owners.

## Modules and files

| Swift root | Kotlin root | Responsibility |
| --- | --- | --- |
| packages/FoundryQuery/Sources/FoundryQuery/QueryState.swift | project/core/query/src/main/kotlin/dev/mobilefoundry/query/QueryState.kt | Generic state, projections, pure transitions |
| packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Query/QueryContent.swift | project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/feedback/query/QueryContent.kt | Copy, native async presentation, content slot |
| apps/FoundryCatalog/Sources/Query/QueryCatalogView.swift | project/app/src/main/java/dev/mobilefoundry/catalog/ui/query/QueryCatalogScreen.kt | Deterministic state gallery with scalar payload |

Query depends only on kernel. UI depends on query/kernel and its native UI
toolkit. Neither imports services, HTTP, or app code. Add package/module
manifests, tests, catalog navigation, app dependencies, and mirrored notes.
The notes state owners use QueryState directly; remove their duplicate state
declarations and redundant previous-snapshot fields. NotesScreen supplies its
copy, emptiness rule, and domain content slot to QueryContent.

## State and side effects

Pure transformations preserve empty snapshots. Existing generation and activity
checks run before settling results. Cancellation restores the current snapshot.
UI emits callbacks; the host handles effects. The gallery manually changes
state and runs no network or asynchronous work.

## Verification and completion

Shared fixtures cover all phases with absent, populated, and empty snapshots,
plus repeated refresh/failure/restoration. Run query package/unit suites,
existing notes store/ViewModel races, both app builds and native presentation
checks. Run notes-check and diff checks. Record observed checks in the notebook.
No new generic request runtime is justified by the two consumers: one owns
asynchronous reads, while the other is a pure presentation gallery.

The later [token slice](ui-tokens.md) places QueryContent and QueryCopy together
under the feedback/query component family. Swift public names remain unchanged;
Kotlin imports follow the new component package. Token/theme code stays in the
UI library, leaving query values independent of native UI frameworks.

## Completion evidence

Completed 2026-10-08: two query tests on each platform against nine shared cases,
seven iOS store tests, eight Kotlin notes ViewModel tests and two starter tests,
both native consumers built, and ten Android instrumented tests. Manual iOS
checks covered initial/retained states, empty retention, public internal failure,
Retry/Cancel, and notes content through the extracted component. A screenshot
confirmed the populated gallery's basic layout. Notebook walkthroughs record
remaining accessibility and device limits.

# Notes query construction specification

## Status and scope

Implemented 2026-10-08. First read-only service/provider seam: a notes list with load, refresh,
empty, failure and cancellation behavior on both native platforms.

## Exemplar workflow

Choose memory or HTTP in the catalog, load notes, retry a failure, and replace
a slow request. The same screen consumes both implementations. No backend runs.

## Source documents and authority

[Architecture](../../ARCHITECTURE.md), [organization](../ORGANIZATION.md),
[notes/query contract](../../contracts/behavior/notes-query.md), and
[HTTP contract](../../contracts/behavior/http.md) control ownership and behavior.
Domain, feature state and verification are established by the notes contract.
Persistence is not applicable. Provider SDK semantics are deferred.

## Nouns, states, and invariants

Note, NotesService, memory/HTTP adapters, and an injected feature state owner.
IDs are unique; provider ordering survives. Loading and failure can retain the
previous successful list. Generations protect admission of asynchronous results.

## Workflow and side-effect ordering

Composition constructs the adapter, then the state owner. A load advances its
generation and cancels prior work before publishing Loading. The service fetches
and validates. The state owner admits only the current active result. Screen
removal cancels work. Defects reach diagnostics before generic failure publication.

## Expected file tree

All paths below are required, relative to their owning native root:

| Swift | Kotlin | Responsibility |
| --- | --- | --- |
| packages/FoundryServices/Sources/FoundryServices/NotesService.swift | project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesService.kt | Domain values, port, memory and HTTP implementations |
| apps/FoundryCatalog/Sources/Notes/NotesStore.swift | project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesViewModel.kt | Feature query state and owned work |
| apps/FoundryCatalog/Sources/Notes/NotesScreen.swift | project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesScreen.kt | State rendering and callbacks |
| apps/FoundryCatalog/Sources/Notes/NotesCatalogView.swift | project/app/src/main/java/dev/mobilefoundry/catalog/ui/notes/NotesCatalogScreen.kt | Catalog controls and state-owner lifecycle |
| apps/FoundryCatalog/Sources/Composition/NotesComposition.swift | project/app/src/main/java/dev/mobilefoundry/catalog/composition/NotesComposition.kt | Adapter selection and scenario injection |
| packages/FoundryServices/Tests/FoundryServicesTests/NotesServiceTests.swift | project/core/services/src/test/kotlin/dev/mobilefoundry/services/NotesServiceTest.kt | Shared response admission and adapter semantics |
| apps/FoundryCatalog/Tests/NotesStoreTests.swift | project/app/src/test/java/dev/mobilefoundry/catalog/ui/notes/NotesViewModelTest.kt | State/lifetime and provider substitution checks |

Shared `contracts/fixtures/notes/responses.json`, native navigation updates,
the Xcode unit-test target, Android device tests, Makefile test commands and
learning notes are required integration files. A generic query framework,
new runtime modules, persistence migrations and provider SDK dependencies were
deferred in this initial slice. The subsequent [query/UI extraction](query-ui.md)
adds reusable value-state and presentation modules without a query runtime.

## File specifications

Services own Note admission, the list port, snapshot memory storage and HTTP
response projection. They depend on kernel and HTTP, never UI or app code.
HTTP options remain inside the adapter. State-owner files depend on the domain
port and native observation/lifetime APIs, never concrete provider construction.
Screen files render state and dispatch load/cancel callbacks; composition files
alone choose adapters. Catalog containers own scenario controls. Test files
verify these responsibilities through public behavior rather than source shape.

## Cross-file dependency map

Catalog composition → state owner → NotesService → memory or HTTP adapter.
Screen → domain state and callbacks. HTTP adapter → HTTPClient → HTTPTransport.

## Transport and persistence mapping

GET notes maps an admitted JSON list to Notes. Expected HTTP failures retain
their identity. Memory returns a snapshot. Neither implementation persists edits.

## Verification plan

Shared fixtures cover populated/empty lists, extra fields, invalid roots/items,
wrong types, blank titles, invalid/duplicate IDs and expected HTTP failures.
State tests cover both providers, previous-data retention, explicit retry,
replacement, cancellation (including late completion) and diagnostic identity.
Build both apps and exercise provider/scenario switching through catalog UI.

## Construction order

1. Contract and fixtures, domain port and adapters, service tests.
2. State owners and deterministic asynchronous tests.
3. Composition, screens and navigation, native builds and device checks.
4. Learning walkthroughs, evidence and limitations, notes checks.

## Open decisions and unknowns

No unresolved decision blocks this bounded slice. Cache identity, writes,
account scope, persistence and real managed providers need separate contracts.

## Deviation record

| Location | Specification | Actual behavior and reason | Decision needed? |
| --- | --- | --- | --- |
| Android catalog composition | Select a state owner with each provider/scenario | Retains at most eight keyed ViewModels within the navigation entry, cancels outgoing work, and refreshes a revisited selection. Uses the existing Lifecycle 2.10.0 navigation entry owner; Swift uses a fresh store. The contract records this native lifetime difference. | No |

## Completion criteria

Both providers pass the same feature checks. Late work cannot overwrite newer
state. Device evidence and notes distinguish injected HTTP from a live backend.

Completed checks: five Swift and five Kotlin service tests (including the two
existing health tests on each platform), seven iOS store tests, eight Kotlin
ViewModel tests plus two starter tests, both app builds, and six Android device
tests. iOS accessibility state confirmed both providers' content and HTTP empty/
failure presentation; blank screenshot output prevented visual layout review.
See [the learning index](../../notes/README.md) for source walkthroughs and limits.

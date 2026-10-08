# Notes service and query state

Status: Established for the read-only provider seam slice, 2026-10-08.

## Domain and service

A Note has an immutable ID and title. IDs contain 1–64 ASCII letters, digits,
hyphens or underscores. Titles contain at least one character other than ASCII
space, tab, carriage return or newline. Titles are preserved without trimming.
Lists have unique IDs and preserve provider order; an empty list is success.

NotesService has one asynchronous `list` operation returning AppResult of notes.
It exposes no HTTP or provider SDK types. Expected failures remain values;
structured cancellation and unexpected defects propagate to its caller.
The in-memory implementation owns a snapshot of validated, uniquely identified
notes. Invalid seed data is a programmer error, not a dependency failure.

The HTTP implementation requests GET `notes` relative to its injected client.
Its successful entity must be an object containing a `notes` array of objects
with valid string `id` and `title`. Extra fields are ignored. The entire list
is refused if any item is invalid or IDs repeat. Refusal is
internal/notes.invalid_response, preserving request/correlation IDs. HTTP
failures pass through unchanged. HTTP timing is configured at composition.
This is a catalog wire example, not a deployed backend API.

## Feature state and lifetime

The Swift store and Kotlin ViewModel receive the service interface. Their
states are Idle, Loading(previous?), Loaded(notes), and Failed(failure, previous?).
These now use the generic [QueryState contract](query-ui.md), with notes-specific
copy and rows supplied to QueryContent. Loaded([]) renders an explicit empty state. Refresh retains the last successful
snapshot while loading and after a failed refresh, including an empty snapshot.
Retry is an explicit user action; there are no automatic retries.

Each load replaces owned work and advances a generation. Only the current
generation may publish success, expected failure, or a caught defect. Explicit
cancel or screen removal cancels owned work and restores the last successful
snapshot, otherwise Idle. A cancellation never appears as a failure. Generation
checks also reject late responses from dependencies that ignore cancellation.

Unexpected exceptions reach an injected diagnostic callback at the presentation
boundary and produce a deliberate internal failure with generic public copy.
The service adapters themselves do not catch arbitrary defects.

## Composition and verification

App composition chooses memory or HTTP and supplies catalog-only content,
empty, unavailable and slow scenarios. The HTTP catalog uses an injected wire
transport; the list screen reads domain state and dispatches callbacks only.
Provider/scenario replacement selects a separate query scope and cancels outgoing
work. Swift creates a fresh store for a changed selection; Android retains one
ViewModel per selection within the navigation entry, so returning to that
selection refreshes its last snapshot. These are presentation lifetime policies,
not a shared query cache.
Android ViewModels are scoped to navigation entries and survive configuration
changes; leaving composition cancels active reads, and entry removal clears them.

Both service suites consume shared admission fixtures. Feature tests exercise
memory and HTTP through the same state owner, refresh retention, error recovery,
latest-result admission, cancellation and defect reporting. Device checks
exercise both implementations through the same rendered screen.

The separate [forms/mutations contract](forms-mutations.md) adds NoteCreator
without broadening this read port. Its catalog write instance does not update
this read gallery; shared data ownership and invalidation remain separate work.

Persistent storage, query caches, deduplication, account scoping,
credential renewal, provider SDK adapters and offline synchronization are deferred.

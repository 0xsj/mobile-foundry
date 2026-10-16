# Current snapshots and subscription lifetime

Claim: a live read capability can publish current local data without owning the
writer, its receipts, or the feature's screen lifetime.

Origin: native foundation observation batch, 2026-10-10. Evidence comes from the
[contract](../../contracts/behavior/notes-observation.md),
[shared scenario](../../contracts/fixtures/notes-observation/scenario.json),
native repository tests and catalog owner/UI checks. Follow native reading step 44
for source and actual platform evidence.

## What and why

Keep NotesObserver separate from CRUD. A dashboard reader needs snapshots; an
editor needs commands; some providers support only one capability. Composition
chooses an ObservableNotesRepository without forcing every service fake to stream.
Expected read failures remain result values. Unexpected defects terminate a
subscription and belong at the current feature's diagnostic boundary.

The source publishes current owned state, including empty lists. Intermediate
states may coalesce, and equal state may be suppressed. A create receipt confirms
that create; a later snapshot describes the collection now. Counting emissions
cannot measure writes or establish an audit history.

SQLite adapters register an invalidation listener before the first read. An
in-process committed write signals that path; the subscriber rereads with a
scoped handle. A signal arriving during a read leaves a bounded pending signal
for another read. Registering after the read would leave a missed-write window.
Notifications originate inside the storage operation after commit, independently
of whether the writer's UI later accepts its receipt.

## Example and ownership

Open Live notes and create one note. Both readers show it without a refresh.
Pause the second reader and rename the note. The first follows the change, while
the second keeps its old snapshot. Resume it to start a new subscription and
read current state. Deleting the last note produces a present empty snapshot.

One feature owner holds one task/job, query snapshot and generation. Pause drops
observation but retains content. Resume reads fresh state. Terminal shutdown
clears content and rejects deferred callbacks. Account readers additionally admit
their exact session lease before starting and publishing; their parent destroys
them when access closes. Storage identity outlives this presentation ownership.

## Gotchas and evidence limits

Swift's explicit subscription cancellation covers early exit and never-iterated
streams; native task cancellation alone is not an ownership API for those cases.
Kotlin's cold Flow registers on collection and unregisters through structured
cleanup. Both need bounded state delivery and independently cancelable readers.

A source may return a read failure and remain subscribed. The catalog retains
previous content, and a later successful write or explicit restart can recover.
There is no retry timer. Generations suppress superseded values/errors, including
sources that ignore cancellation. Error callbacks can close access themselves;
publication must recheck admission afterward.

Current evidence covers Memory, actual SQLite files, cross-instance readers,
coalescing, independent cancellation, native account switching and Android
Activity recreation. Notifications cover this process and admitted path/name,
not external SQL, file aliases, other processes, background workers or a backend.
Foreground/restart reads catch current local data. No query cache, schema change,
outbox, reconciliation or remote authorization is added.

Related: [local commits](local-commits-and-observation-lifetime.md),
[leased work](account-partitions-and-leased-work.md),
[construction](../../docs/blueprints/notes-observation.md),
[Swift streams](../../frontend/swift/notes/substrate/async-stream-buffering-and-subscription-cleanup.md)
and [Kotlin flows](../../frontend/kotlin/notes/substrate/flow-invalidation-and-lifecycle-collection.md).
Next: which product needs paging, an observable remote cache or a transactional
outbox, and what new consistency contract should that capability promise?

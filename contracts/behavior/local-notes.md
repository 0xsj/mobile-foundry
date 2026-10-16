# Durable local notes

Status: Implemented on Swift and Kotlin, 2026-10-10. This is the first
local-data slice; Notes is the reference domain for reusable repository seams.

## Domain boundary

NotesRepository composes NotesService and NoteCreator, adding asynchronous
`update(UpdateNote)` and `delete(DeleteNote)`. It returns kernel AppResult values
without storage/SDK types. Cancellation and unexpected defects propagate.
Existing read-only and create-only consumers keep their narrow ports.

UpdateNote admits the existing note-ID grammar and CreateNote title rules.
DeleteNote admits the same ID grammar. Invalid IDs return invalid/notes.invalid_id
with the id field. Invalid titles retain invalid/notes.invalid_title and title
copy. Titles preserve input, including whitespace, Unicode, quotes, line breaks
and embedded NUL; the 200-character limit counts Unicode scalars/code points.
The shared [fixture](../fixtures/local-notes/repository.json) specifies examples.

Create assigns a unique UUID string. Lists are owned snapshots in insertion
order. Update preserves ID and position. Delete removes only its admitted ID.
Unknown update/delete IDs return not_found/notes.not_found; deletion is not
silently admitted twice. Duplicate create requests can create separate notes.
No automatic retries or idempotency mechanism is supplied.

## Storage and versioning

FoundryData/core:data owns SQLiteNotesRepository; Services owns domain ports and
the mutable InMemoryNotesRepository. App composition selects the adapter and
private database location. New databases start empty; opening does not seed data.
Swift uses Application Support/LocalNotes/notes.sqlite. Android uses the app
database directory/local-notes.sqlite. This is one device-local guest collection,
without authenticated account scope or remote ownership.

Version one uses user_version=1 and a notes table with insertion position as
INTEGER PRIMARY KEY AUTOINCREMENT, a unique nonnull text ID and nonnull text
title. Bind values as parameters; never interpolate supplied text into SQL.
Swift uses explicit UTF-8 lengths/copies and byte-counted reads to preserve NUL.
Android uses native bound ContentValues/selection arguments and cursor reads.

Single-statement CRUD uses SQLite atomic transactions. Swift serializes each
instance with an actor; Android uses a mutex and Dispatchers.IO. Independent
instances coordinate through native SQLite locking. Each operation closes its
connection/helper, statements and cursors. No database handle lives in UI state.
Constructors do no disk work. UI rendering and app lifecycle stay outside Data.

Schema initialization is transactional. Existing unversioned application tables
are refused; Android's native android_metadata table is exempt. Versions newer
than one return unavailable/notes.storage_version and are not downgraded. No
version-two migration is claimed. Unreadable/corrupt files and invalid decoded
rows produce typed storage failures rather than partial lists. Raw paths and
native exception messages do not become public copy. Other native IO/locking
failures remain typed unavailable/conflict values. Corrupt data is never reset
automatically; Android overrides the default destructive corruption handler.

Cancellation is checked before owned disk work and before mutation dispatch.
Cancellation after a commit can suppress acknowledgement; it does not establish
rollback. An abandoned write may already be visible to a fresh read. Stored notes
survive owner/route recreation and process relaunch within the same installed
app container. Uninstall, app-data clearing and OS backup/restore are external
platform policies; this slice adds no backup/export feature.

## Feature admission and presentation

Open Studio → Open catalog → Local notes. Device storage is the default.
Memory uses the same CRUD state/rendering and remains transient. Provider
changes select a separate feature scope and stop outgoing observation. Swift
discards its replaced memory owner; Android retains one owner per source in the
navigation entry and discards it when the entry closes. Saved device records
remain shared through the database path.

Initial load must succeed before writes/editing are enabled. A refresh may retain
the previous list on failure. Refresh, save, delete, edit and source replacement
cannot overlap a current operation. Save validates synchronously and marks busy
before dispatch; button/Return/IME share that admission. Unsaved drafts and delete
presentation are transient. Editing a record fills the draft; New note abandons
it. Delete requires explicit confirmation of the captured note; cancel/dismiss
has no write effect.

After create/update succeeds, the draft clears. Deleting the currently edited
note clears that draft; deleting another preserves it. The feature retains a
commit receipt and reloads the repository snapshot before re-enabling actions.
A failed post-commit read preserves that receipt, retains the last snapshot and
offers explicit refresh. It does not report rollback or replay the write.
Expected write failures preserve the draft. Known field errors render alongside
Title; summaries project kernel publicInfo.

Each operation has a generation. Stop/route/source removal cancels owned work and
invalidates result and defect admission. An unconfirmed submitting operation gets
deliberate canceled feedback instructing a refresh before retry. A confirmed
receipt is retained even if its subsequent read is stopped. A late dependency
cannot publish into the abandoned owner; a fresh owner can observe committed data.

## Verification and limits

Native file tests cover fresh handles, CRUD identity/order, Unicode/NUL round-trip,
concurrent creates, unsupported/corrupt/unversioned stores and pre-canceled work.
State-owner checks cover duplicate admission, late commits and post-commit read
failure. Native Android UI checks cover source parity, validation, explicit retry,
confirmed/canceled deletion and reopening after navigation-entry removal.
Android also checks a rapid Compose key burst and current native text admission;
this does not establish OS keyboard window routing for every IME configuration.
Actual execution counts and relaunch observations belong in learning notes.

No database observation stream, query cache, background scheduling, account/session
layer, outbox, remote adapter, synchronization or conflict reconciliation is added.
These require their own contracts and consumers. This slice reuses existing UI
components and does not increase the 112-component/30-family catalog count.

# Account Notes read cache

CLI 0.16.0 selects this capability in Daily + go-connected on Swift/Kotlin.
NotesReadCache is a service-owned port with Memory and native SQLite adapters.
It stores advisory first-page content; RemoteNotesRepository remains the source
of authoritative reads and writes. Catalog/local guest repositories keep their
existing behavior.

## Admission and storage

- Reuse the admitted deployment/principal/account tuple from PendingNotesScope.
  No credentials, session epochs, passwords or recovery codes enter the cache.
- A versioned snapshot contains at most twenty distinct admitted VersionedNotes,
  a positive safe-integer saved time and the journal command/status checkpoint
  observed at that read. Exact keys, UTF-8 and a 64 KiB payload bound apply. Shared
  fixtures define native parity. Cursors and subsequent pages are never stored.
- Memory/native stores retain at most 32 scopes, evicting oldest saved snapshots.
  A save cannot replace a newer timestamp. Snapshots are usable for less than
  24 hours; future timestamps and expired entries are removed on load. This is
  lazy usability/cleanup, not scheduled physical erasure of every old record.
- Native SQLite uses a separate private, backup-excluded file and schema marker.
  Each operation owns a short transaction. Unknown/corrupt stores are preserved
  and refused, including accidental use of a pending-journal database. Cache
  deletion/eviction never opens, resets or prunes the pending-command journal.

## Product ownership

Only a current authenticated account binding can expose saved content. Cold
credential restoration still needs the API; the cache cannot establish identity,
membership, token renewal or offline sign-in. Sign-out/account replacement hides
content immediately and cancels old work. After every suspension the owner checks
cancellation before publication; late cache replies cannot populate a new owner.

Restore the journal first. Compare the cache checkpoint against that restored
record before showing anything. A changed pending/confirmed/rejected command
invalidates a pre-mutation snapshot without coupling two SQLite transactions.
An unreadable journal prevents fallback. This checkpoint does not establish
server-wide freshness, a sync cursor or causal ordering across other clients.

Show a usable saved first page while its fresh read is pending and after
unavailable/timeout failures. Label its saved time and read-only status; disable
all new writes and pagination until a fresh authoritative page succeeds. An empty
cached page must also show the cached label. Other server failures hide saved
content. Unauthenticated/forbidden responses additionally attempt scoped cache
removal and suppress fallback for that binding; removal failures are reported.
Server-side membership/revocation cannot be discovered without a connection.

First-page HTTP success replaces visible content and saves a snapshot with the
current journal checkpoint. Cache failures are separate warnings: fresh server
data and readable pending-command recovery remain usable. A successful write
still requires journal receipt persistence; an old cache cannot undo that rule.
No network request or transaction is held inside a cache operation.

Remove saved copy deletes only this scope's cached page. It hides cached content
on success; fresh content may remain visible. A later successful first-page read
saves another copy. Sign-out retains disk snapshots behind the session gate;
this slice adds no account erasure workflow or scheduled retention worker.

## Acceptance

Check shared admission, expiry/clock rollback, scope partition, bounds/eviction,
reopen, malformed/future schema preservation and journal-file refusal. Product
owners additionally check late cancellation, authorization refusal, optional
storage failure and checkpoint invalidation after a confirmed mutation.

Independent generated UI acceptance uses real HTTP followed by an account-scoped
test-proxy outage. Verify saved-page rendering after process restart, disabled
writes, account isolation, explicit removal and fresh-read recovery. The proxy
control route exists only in the copied acceptance script, never in Go or the app.
Read [construction/evidence](../../docs/blueprints/notes-read-cache.md).
Automatic replay, offline writes, multi-command queues, conflict merging and
background synchronization remain separate capabilities.

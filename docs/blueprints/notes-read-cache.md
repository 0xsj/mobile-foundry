# Scoped native Notes caching

CLI 0.16.0 selects NotesReadCache for Daily + go-connected. Read the
[behavior boundary](../../contracts/behavior/notes-read-cache.md) and
[checkpoint reasoning](../../notes/patterns/cached-content-and-command-checkpoints.md).

The reusable seam is in Services; native disk adapters are in Data. Product
overlays own cached/fresh presentation, session gating and network fallback.
Replace Memory/SQLite through the port without changing the HTTP repository.

## Source reading order

1. [Swift snapshot/port](../../frontend/swift/packages/FoundryServices/Sources/FoundryServices/NotesReadCache.swift)
   and [Kotlin snapshot/port](../../frontend/kotlin/project/core/services/src/main/kotlin/dev/mobilefoundry/services/NotesReadCache.kt).
2. [Shared admission vectors](../../contracts/fixtures/notes-read-cache/snapshots.json).
3. [Swift SQLite](../../frontend/swift/packages/FoundryData/Sources/FoundryData/SQLiteNotesReadCache.swift)
   and [Android SQLite](../../frontend/kotlin/project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesReadCache.kt).
4. [Swift product owner](../../templates/overlays/go-connected/swift/RemoteNotesStore.swift)
   and [Kotlin product owner](../../templates/overlays/go-connected/kotlin/RemoteNotesStore.kt).
5. [Independent acceptance driver](../../scripts/connected_acceptance.py).

## Try it

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Follow SETUP.md to register/sign in and select an account. Read or create a Note
in Library, then interrupt connectivity while the authenticated lease is current.
Refresh shows the saved first page and saved time; editing stays disabled until
a fresh read succeeds. Remove saved copy clears that scope's cached snapshot.
Restoring a cold session still requires the API. For repeatable local device
acceptance, stop the development API and run the generated project's own check:

```sh
make connected-test CONNECTED_PLATFORM=both
```

The owned proxy can interrupt only Notes reads while identity/account restore
continues. This distinguishes cache process recovery from offline sign-in.
It never changes product transports or adds product/backend debug routes.

## Observed verification — 2026-10-17

CLI 0.16.0 generated the untouched independent output at
`/private/tmp/mobile-read-cache-final-pomd2psd/read-cache-final`, installed identity
`com.example.readcachefinal`, module `github.com/example/read-cache-final/backend`.
Its own `make connected-test CONNECTED_PLATFORM=both` passes. Runtime cache files,
product owners, fixtures and the copied driver match their source versions; no
post-generation source patches were applied to this final copy.

- iPhone 17 Pro/iOS 26.2: **22 checks passed, zero failures/skips**: nineteen hosted
  state/presentation checks and three UI scenarios. Saved-page process restart,
  read-only gating, account isolation, removal and fresh recovery run alongside
  original CRUD/receipts, renewal, Keychain restoration, verification/reset/change
  forms and external-write socket invalidation.
- Pixel 9 Pro/Android 15: **six UI stages each pass once without failures/skips**:
  write, restore, retry, security, cache-write and cache-restore. The install-once
  driver force-stops between process stages without clearing private app data.
- **Seven native SQLite cases per platform pass**, including the three new cache
  cases and four pending-journal cases. Corruption/future schema, target-row refusal,
  scope partition, checkpoint survival, expiry/clock rollback and journal-file
  preservation are exercised by the appropriate storage/owner layers.
- **Fifteen Kotlin product-state cases pass without failure/skip**. Swift owner
  cases run within the nineteen hosted checks. Shared Services snapshot vectors
  and Memory cases pass on both platforms. Kotlin host tasks explicitly rerun
  without build-cache test reuse; the real HTTP/socket/mail cases execute against
  this run's owned Go API.
- The existing proxy assertions still observe one original commit, exact command
  ID/input retention, explicit receipt lookup/retry and no automatic replay.
  The added read outage affects only the target account's Notes page, allowing
  real online credential restoration before cached rendering after restart.

The final log is `/private/tmp/mobile-read-cache-final.log`. iOS results are in
`.cache/connected-ios/Results/connected-1792264980027053000.xcresult` within the
copy; Android stage receipts are under `.cache/connected-android`, with native
SQLite XML under `frontend/kotlin/project/core/data/build/outputs/androidTest-results`.
All 32 CLI cases pass; notes/inventory checks and `git diff --check` pass.
The driver removes its owned API, SMTP fixture and reverse mappings.

Daily + go-connected remains **connected starter ready**, now through CLI 0.16.0
for this selected scope. Cold offline sign-in, automatic sync/replay, multi-command
queues, richer retention/erasure, generated Files/inbox/push, release/cloud delivery
and physical-device performance remain deliberate extensions. The cache is an
advisory read capability; Go retains its separate PostgreSQL/operations evidence.

Review found a native parity issue before the final run: Swift's upsert could
replace a corrupt target payload without admission, while Android read/refused
that row first. Both adapters now validate the target before saving. Native
checks explicitly refuse a save after corrupting the row, then confirm it remains
unreadable. Successful server content remains usable with a separate cache
warning; no implicit repair policy is introduced.

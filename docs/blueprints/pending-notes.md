# Pending Notes recovery construction

CLI 0.13.0 extends Daily + go-connected with native disk recovery. The capability
is one saved change per deployment/user/account, not a general offline engine.
Read [behavior](../../contracts/behavior/pending-notes.md) and
[connected setup](go-connected-starter.md).

## Source order

1. Services owns [Swift pending models/port](../../frontend/swift/packages/FoundryServices/Sources/FoundryServices/PendingNotes.swift)
   and [Kotlin counterparts](../../frontend/kotlin/project/core/services/src/main/kotlin/dev/mobilefoundry/services/PendingNotes.kt).
2. Data implements [Swift SQLite](../../frontend/swift/packages/FoundryData/Sources/FoundryData/SQLitePendingNotesStore.swift)
   and [Android SQLite](../../frontend/kotlin/project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLitePendingNotesStore.kt).
3. Product overlays own [Swift state](../../templates/overlays/go-connected/swift/RemoteNotesStore.swift)
   and [Kotlin state](../../templates/overlays/go-connected/kotlin/RemoteNotesStore.kt).
   Composition explicitly injects SQLite; owner tests inject Memory/failure adapters.
4. [Inventory](../../templates/profiles/native-daily-go-connected.json) selects only
   the pending adapter/tests from Data. It does not copy account-local or guest
   CRUD, catalog code, or Data's full Session dependency into this product.
5. [Acceptance driver](../../scripts/connected_acceptance.py) runs from the copied
   project with its own Go source, API, fault proxy and users.

The slot retains pending, matching receipt, or known rejection. Stage checks exact
replay before comparing the previous terminal command ID. Confirmation/rejection
compares the original command again inside the transaction. Replacing the slot
requires explicit new work and the terminal ID; a delayed old response cannot
settle a later command. SQL handles close before HTTP begins.

The app loads recovery before enabling writes. Failed or uncertain staging never
sends HTTP. Failed receipt persistence keeps the observed receipt and pending UI;
a later load establishes what actually committed locally. Restoring saved work,
refreshing or returning to foreground never resends it. Check receipt and Retry
same change reuse original input through a current exact lease for its scope.

## Storage and product boundaries

The v1 JSON record is bounded to 16 KiB with strict UTF-8, owned fields, admitted
models and status consistency. The default cap is 256 scope slots. There is no
automatic deletion, pruning, expiry, multi-item queue, local Notes cache, merge
policy or background worker. Unknown/corrupt storage fails closed and is kept.
A product must design explicit export/erasure/migration policy before adding one.

Generated iOS stores under its Application Support/PendingNotes directory,
excluded from backup and protected until first authentication. Android uses
noBackupFilesDir. The record contains note input and receipts but no passwords,
tokens or lease epochs. Sign-out keeps scoped device work; uninstall can remove it.
Authentication restore and remote reads still require the API. This slice does
not make offline launch usable or change the catalogs' transient recovery owners.

## Checks

Shared [records](../../contracts/fixtures/pending-notes/records.json) exercise
create/update/delete, pending/terminal statuses, malformed fields, owner/version
mismatch and Unicode-exact receipt input. Service tests also cover malformed UTF-8,
size bounds, CAS, replay, capacity and stale settlement. Native Data tests use real
SQLite files for reopen, partitions, rollback, corruption/future-schema refusal
and competing instances. Product owner tests inject uncertain local outcomes.

From this source checkout, `make data-test` covers the broader Services/Data
regressions; `make pending-notes-ui-test` executes the new Android SQLite cases
on a booted emulator. Product overlay owners are checked in an independent copy.

In an independently generated connected project:

```sh
make swift-test
make ios-test
make android-test
make android-ui-test
make connected-test CONNECTED_PLATFORM=both
```

The connected driver owns an HTTP fault proxy in front of an isolated memory API.
A committed first attempt receives a replacement 503 instead of its receipt; an
unsent first attempt is refused without forwarding. iOS terminates/relaunches
inside XCUITest; Android installs once and runs three instrumentation processes
with force-stops. Both must load the original saved ID before recovery. Missing
receipt keeps pending, and explicit retry must keep method/path/body/command ID.
The proxy requires one committed write per scenario and no automatic replay.
These are local Debug checks; power-loss durability, device budgets, release signing,
cloud deployments, and offline synchronization need separate evidence.

## Observed verification — 2026-10-17

Final untouched CLI 0.13.0 output:
`/private/tmp/mobile-pending-starter-4hc0bs4d/recovery-verified`, installed identity
`com.example.recoveryverified`, module `github.com/example/recovery-verified/backend`,
Debug API `http://127.0.0.1:19899/v1`. Its source was not patched after generation.

- Its copied `make connected-test` passes both platforms against its own compiled
  Go API and fault proxy. Real URLSession/OkHttp session/accounts and Notes receipt/
  CAS cases execute; both selected screen workflows finish without failure/skip.
- iPhone 17 Pro / iOS 26.2: one XCUITest terminates/relaunches for both committed-
  response-withheld and never-forwarded pending commands. It checks the original
  saved ID, explicit receipt lookup, missing-receipt retention and exact-input retry,
  then deletes the recovered notes and signs out. Result:
  `.cache/connected-ios/Results/connected-1792250787262469000.xcresult`.
- Pixel_9_Pro / Android 15: write, restore and retry each execute one instrumentation
  test without skip/failure, with force-stops between runs. Restore also switches
  away/back to prove pending account isolation. Reports:
  `.cache/connected-android/write-1792250926770701000.log`,
  `restore-1792250962553060000.log` and `retry-1792250982382755000.log`.
  The proxy requires exactly one committed write for each fault scenario, original
  method/path/body/command ID, one attempt for committed recovery and two for the
  deliberately unsent recovery. No production debug hooks were needed.
- The final copy's four Swift SQLite tests pass. Its six Services cases have five
  passes and one expected external-API skip in the ordinary run; the owned driver
  separately executes that API case. Shared admission includes strict UTF-8,
  UTF-16 refusal, Unicode-exact input and immutable terminal receipts.
- The sibling independent CLI 0.13.0 output `recovery-ready` uses the same native
  owners/SQLite code and passes nine hosted iOS cases, 71 Android unit cases
  (69 passes, two expected API skips), four Android SQLite cases and two Android
  entry cases. Its three opt-in API UI cases skip in the ordinary run and execute
  without skips in the owned final driver. The final UTF-8 admission guard was
  added only to Swift Services before generating `recovery-verified`.
- Source Swift Data has 18 passes; source Swift Services has 35 cases with the
  external-API case opt-in. The 32 CLI cases pass, including new fixture/selected
  Data closure checks. Inventory and documentation checks pass.

Investigation found two real boundary issues: Android requires rawQuery for the
result-bearing busy_timeout PRAGMA, and the test proxy needed explicit
Connection: close so OkHttp did not reuse a closed connection. Native database
and transport/process checks passed after those corrections. The immutable
terminal-receipt test also guards changing a created note ID under the same command.

The connected Daily starter remains ready for new Swift/Kotlin + Go products,
now including durable manual recovery. The API in this run is isolated memory;
no PostgreSQL code changed in this slice. Earlier backend persistence acceptance
remains separate. Cold dependency install, physical-device performance, sudden
power loss, release signing/deployment, offline launch, erasure/migrations and
multi-command synchronization were not established by these checks.

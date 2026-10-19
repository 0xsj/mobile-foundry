# Optional account Files: CLI 0.25.0

Swift/Kotlin Daily + Files selections are **connected starter ready** with either
Go or Rust for the tested local baseline.
They fill Today's placeholder with a gallery import/draft/upload workflow,
account file pages, verified download previews and confirmed deletion.

```sh
mobile-foundry init media --platform both --with files --with go-connected --backend go \
  --app-id com.example.media
cd media
make go-dev
```

For Rust use --with rust-connected --backend rust and make rust-dev. Select swift
or kotlin for a single native project. These explicit inventories share all
native code and retain the existing connected Notes/account/session foundations.
CLI 0.23.0 separately registers [Files + Inbox](connected-media.md). The binary integration test
also checks upload-to-notification delivery, so its narrow source closure includes
Notifications ports/decoder/fixtures without selecting an inbox screen.

Read [the product contract](../../contracts/behavior/connected-files.md),
[the existing backend/native protocol](../../contracts/behavior/files-media.md)
and [the recovery lesson](../../notes/patterns/file-receipts-and-byte-recovery.md).
The generated SETUP.md describes device endpoints and recovery constraints.

FilesStore now persists one original command and up to 10 MiB of upload bytes in
a separate private SQLite journal before HTTP. It never replays on bind/renewal
or process restore. Explicit upload retry checks the completion receipt before
PUT; delete retry repeats the same DELETE to finish object cleanup. Journal
removal must acknowledge the original record before pending state is released.
Unreadable/foreign/future journals are kept and block new changes. Review offers
Keep or explicit Forget local recovery, without claiming rollback, secure erasure
or completed byte cleanup. A fresh page gates another accepted action.
Read [durable behavior](../../contracts/behavior/pending-files.md) and
[atomic byte staging](../../notes/patterns/atomic-byte-staging-before-transport.md).
Unaccepted gallery drafts remain view-owned. Background/OS transfers, multi-item
queues, camera/GPU editing, object sweeps and public release remain separate.

From a generated output with booted owned devices and an unused Debug endpoint:

```sh
make connected-test CONNECTED_PLATFORM=both CONNECTED_SCENARIO=files
```

The copied runner seeds 25 tiny real image uploads, imports the owned fixture into
the system gallery, and drives actual native pickers and product screens. Its
fault proxy withholds completion/delete responses after commit and verifies one
content PUT, receipt settlement without repeated completion, and identical delete
retry. Current Files acceptance also restores original uploads/deletes across app
termination, including bytes withheld before reaching the backend. There are two
iOS workflows and six Android process stages. This is manual recovery against a
continuously running local API, not backend database-restart or release-device
acceptance. The full scenario adds the base connected suite.

Native acceptance observations (iOS 26.2 / Android 16, 2026-10-19): SwiftUI Form
rows need independent preview/delete button styles; the initial automatic style
did not open the preview reliably. The iOS picker exposes an aggregate grid label
as well as individual images; the test now selects one leaf and taps its observed
image center. Android's API 36 picker requires Done after selecting the photo.
While that external activity is visible, Compose has no app hierarchy, so the
test waits for the product root to return. These are observed test-device details,
not a claim that every supported OS exposes identical picker accessibility.

Original CLI 0.22.0 independent outputs (2026-10-19) live at
/private/tmp/mobile-files-022/media-go-final and
/private/tmp/mobile-files-022/media-rust. Their installed identities are
com.example.mediagofinal022 / com.example.mediarust022; the Go module is
example.com/media-go-final/backend and the Rust package is acorn-api.
The CLI suite passes 51 tests, including six source-equal native/backend copies,
required owner/fixture closure, unsupported combination refusal and base-profile
independence. Starter inventory and documentation checks pass separately.

| Final independent output | Source files | Kotlin host | iOS | Android storage / Files stages |
| --- | --- | --- | --- | --- |
| Go | 776 | 47 passed | 42 passed; full connected suite with Files | 7 / 2 passed |
| Rust | 838 | 47 passed | 37 passed; Files workflow plus hosted owners | 7 / 2 passed |

All selected results have zero failures/errors/skips. Go's final full iOS run
also confirms the established Notes, identity, registration, workspace and cache
workflows alongside Files. The earlier media-go output additionally passes all 12
Android connected stages. That earlier Android source is identical to the final
output; its iOS picker test predates the final selector correction and is not
final iOS evidence. Both final outputs were generated fresh; no program file was
edited after generation and no checkout build output was substituted. The Files
capability/inventories were promoted only after the runs completed. Outputs were
generated while candidate; readiness status changes do not alter program sources.

Recomputed selected-source content digests match the generated metadata:

| Output | SHA256 |
| --- | --- |
| Go | ad8c7aceec04886444d8ab3609369be55aebf95bbdae81ca1da72a2e2b2680c7 |
| Rust | bc006462e6b32ee937981819ec3c30d29e80d3e7e20a6f7ca850b8758647d6fc |

Reports are retained under each output's .cache/connected-ios,
.cache/connected-android and native build/test-results paths. Test devices are
iPhone 17 Pro/iOS 26.2 and Android 16/API 36. Android uses an owned read-only AVD
instance: app force-stops retain its filesystem/Keystore within that instance.
No emulator-reboot, physical-device, public hosting, release-signing/performance,
retained-database native restart or durable/background transfer claim follows.

## CLI 0.24.0 recovery verification — 2026-10-19

The canonical Files owner/UI now supports Review pending operation, Keep pending
operation and explicit Forget local recovery. Review is fenced by command key
and binding generation. Forget is refused while busy or stale, sends no server
mutation, and requires a fresh page before new edits. Upload and delete warnings
explain uncertainty and unfinished cleanup separately. No new backend route or
capability is required; Files-only and combined inventories reuse this source.

Fresh independent outputs under `/private/tmp/mobile-files-024` were generated
with new app/module/package identities and separate Debug endpoints. Generated
sources were not edited. The focused files scenario passed on all four native /
backend pairs:

| Independent output | Selection exercised | Observed checks |
| --- | --- | --- |
| swift-go | Swift + Go, Files only, port 18843 | 40 iOS cases: 39 owners (eight Files) and one real screen workflow; native session/service/storage host suites also pass. |
| kotlin-go | Kotlin + Go, Files + Inbox, port 18845 | 56 host cases (11 Session, six Services, 39 Product), seven device storage cases and two Files screen/process stages. |
| native-rust | Swift/Kotlin + Rust, Files + Inbox, port 18844 | 44 iOS cases (43 owners including eight Files, one screen), 56 Kotlin host cases, seven device storage cases and two Files screen/process stages; Swift native host suites also pass. |

Every counted result has zero failures/errors/skips. After the existing lost
completion and same-delete cleanup retry checks, the proxy commits a second
client's authorized delete under a different key. The product's original delete
then receives a real 404. Screens keep the pending operation once, review again,
forget only local recovery, and observe 24 remaining files before account switch,
renewal, process restoration and sign-out. The boundary asserts exactly one
original terminal attempt, one competing commit and one 404: review/forget sends
no replacement/replay. Owner tests separately inject a version conflict and
verify new command identity after deliberate abandonment, stale dialogs after
renewal/scope changes, busy exclusion and an old upload dialog versus a later delete.

Selected pre-rewrite content SHA256 / file count:

| Output | Files | SHA256 |
| --- | --- | --- |
| swift-go | 528 | e2978d80fc35ce4364e4f449ff90539a4806388d52a72556ec3aea5b2fc69897 |
| kotlin-go | 548 | 5f5e730897a7f1b7a16754cc2aeeaf5af62a713b4a709a9c5312aca3c984892d |
| native-rust | 846 | 0775dba46d6e1d91a13f8f972264c879312b61b3497303df450642f28e6a8fbd |

These match freshly recomputed selected plans after verification. Reports are in
each output's .cache/connected-ios, .cache/connected-android and Gradle XML paths;
summary is `/private/tmp/mobile-files-024/evidence.json`. Logs are swift-go.log,
kotlin-go.log and native-rust-final.log in that parent. Root checks pass 54 CLI
cases, starter inventory, notes/link validation and diff whitespace checks.

The first Rust attempt passed its Kotlin host suites but timed out at the new
screen API's startup gate. An isolated unchanged startup probe immediately
returned 200, followed by the unchanged final both-platform run above. The
initial failure remains in kotlin-rust.log and its cause is unresolved; this is
not evidence of deterministic startup under resource pressure. Startup guards
and product retry policy were not relaxed. This batch reruns focused Files and
owner checks; earlier full-suite/joined-notice evidence above and in the combined
guide remains historical, rather than being relabeled as a new full run.

Toolchain/device observations: Swift 6.2.3, Xcode 26.2, iPhone 17 Pro/iOS 26.2;
Kotlin 2.3.20, Gradle 9.1, Android 16/API 36 read-only emulator; Rust 1.86 and
Python 3.14.8. Emulator app force-stops retain storage only within that instance.
Readiness remains **connected starter ready for the tested local baseline**.
Pending Files still disappear on process exit. Durable byte staging, retained
cleanup responsibility, background transfers, orphan sweep and public release
acceptance remain separate work. Continue with
[the abandonment lesson](../../notes/patterns/explicit-abandonment-is-not-settlement.md).

## CLI 0.25.0 durable recovery verification — 2026-10-19

Files-only and Files + Inbox keep their **connected starter ready** local status
with durable manual recovery. This is the generated product journal, not the
catalog's earlier ephemeral owner or an OS/background transfer queue. Restore
only reads. Exact local stage acknowledgement precedes HTTP; known server
completion and exact journal removal precede releasing pending work. Failed or
unreadable storage stays visible and blocks new changes rather than being reset.

Final independent copies under `/private/tmp/mobile-pending-files-025` use new
app/module/package identities. Sources were not edited after generation, and
selected pre-rewrite fingerprints match current registered source plans:

| Output / selection exercised | Native checks | Files / selected content SHA256 |
| --- | --- | --- |
| final-swift-go: Swift, Go, Files only; port 18883 | 45 iOS cases: 43 app owners (12 Files), two Files workflows. Swift session/service host suites and all 11 Data checks also pass. | 533 / e21dce5fdc0e515cc39fe1dc5fb4cc8d20da51c702ff42489e85939a0d030418 |
| final-kotlin-go: Kotlin, Go, Files + Inbox; port 18885 | 62 host cases (11 Session, eight Services, 43 Product), 11 device storage cases and all six Files process stages. | 553 / e11afdcd832fa3b3d62829e7d09a9a0561168d78024ce8de9b1154de634fb0a7 |
| final-rust: Swift/Kotlin, Rust, Files + Inbox; port 18884 | 49 iOS cases: 47 app owners (12 Files), two Files workflows; Swift host suites/11 Data checks; 62 Kotlin host cases, 11 device storage cases and all six Files stages. | 855 / c7bfe9816e2c8da0a014986246b48d408c13e6dfb0bd01a7288de860ea86ae51 |

All counted final results have zero failures/errors/skips. Each native/backend
pair terminates/force-stops after a lost completion and after an original delete's
lost acknowledgement, restores pending state without replay, and explicitly
settles the original command. Another user imports a photo whose first content
PUT is refused before reaching the API. A fresh process retries using disk bytes,
original specification/key/precondition and no second gallery selection. The
proxy requires two identical request-upload inputs, two byte-identical PUT
attempts with only one forwarded, and one completion. The resulting one-file
page and verified download establish the completed transfer. Existing terminal
Keep/Forget, paging, account isolation, renewal and sign-out also pass.

Shared admission has 14 cases, including canonical IDs, extra fields, mismatched
bytes and terminal delete state. Native disk checks exercise the full 10 MiB
limit, exact removal versus a newer record, competing connections, rollback and
foreign/future/corrupt refusal. Four new owner checks cover cold restoration,
lost stage acknowledgement with no HTTP, failed settlement/Forget removal,
unreadable load and a noncooperative cancelled stage. The full-size Swift disk
case runs on the host; Android's full-size case runs on the test device. iOS
screens use the owned small gallery fixture, not a physical-device payload budget.

Summary: `/private/tmp/mobile-pending-files-025/evidence.json`. Per-output reports
are .cache/connected-ios/Results, .cache/connected-android and native XML folders.
Outer final logs are final-swift-go.log, final-kotlin-go.log,
final-kotlin-rust.log and final-swift-rust.log in the parent. CLI copy/admission
checks pass 54 tests; starter inventory, notes/links and whitespace checks pass.
This reruns focused Files and all app owners, not the entire full/joined-notice
suite; older versioned results above and in the combined guide remain historical.

### Host and test-boundary corrections

Initial default SwiftPM manifest evaluation stalled in this execution host. A
fresh scratch build with --disable-sandbox passed; final generated Swift host
suites explicitly set CONNECTED_SWIFT_DISABLE_SANDBOX=1. This is an opt-in for
host manifest checks only. Normal SwiftPM sandboxing stays the default, and app
sandbox/signing settings are unchanged. This observation is not a general diagnosis
of all SwiftPM stalls.

The original five-second API startup gate also timed out before native tests;
the diagnostic child binary later served normally. The owned runner now uses a
30-second monotonic deadline and preserves pre-fixture failure logs under
.cache/connected-runtime. Product timeout/retry/auth policy was not relaxed.
The earlier verify-swift-go run passed 44 cases and failed only the unsent-content
UI workflow: its proxy compared raw request-upload JSON after process restart.
Swift's dictionary-backed JSON encoding can reorder object members. The final
proxy canonicalizes only JSON syntax, still compares every original field/key,
and keeps PUT content byte-exact. The final fresh copies above pass that boundary.
Initial smoke/failed outputs remain separate and are not counted as final proof.

Tools/devices: Swift 6.2.3/Xcode 26.2, iPhone 17 Pro/iOS 26.2 simulator; Kotlin
2.3.20/Gradle 9.1, Android 16/API 36 read-only emulator; Rust 1.86. The API remains
an owned in-memory process while native apps restart. No backend PostgreSQL
restart, emulator reboot, physical-device, public deployment, release-signing,
performance, offline-authentication or automatic/background sync claim follows.
Private backup-excluded recovery is not an application-encrypted vault, and row
removal is not a secure-erasure guarantee. App removal/storage loss and unreadable
journals still need explicit product recovery/retention policy. Continue with
[the staging lesson](../../notes/patterns/atomic-byte-staging-before-transport.md).

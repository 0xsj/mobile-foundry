# Files and Inbox together: CLI 0.26.0

Swift/Kotlin Daily + Files + Inbox selections are **connected starter ready**
with either Go or Rust for the tested local baseline. They compose existing
account features into one product, so an actual gallery upload can produce
Activity through backend delivery.

CLI 0.26.0 adds Open file to each notice: a current authorized metadata read,
then an explicit verified download preview. A removed target shows unavailable;
permission/dependency failures stay distinct. Opening leaves the notice unread,
never switches accounts, and every exact-lease rebind closes the detail. Read
[destination behavior](../../contracts/behavior/notice-destinations.md) and
[the learning pattern](../../notes/patterns/notice-targets-and-current-authority.md).

```sh
mobile-foundry init canopy --platform both --with files --with inbox \
  --with go-connected --backend go --app-id com.example.canopy
cd canopy
make go-dev
```

For Rust use --with rust-connected --backend rust and make rust-dev. Select
swift or kotlin instead of both for one native product. This is an explicit
combined inventory; the CLI does not merge arbitrary overlays.

Today selects Files, Activity selects Inbox, Library keeps remote Notes and
Account keeps the established identity/workspace flows. Opening Activity requests
a fresh authoritative read. A delayed delivery can still require Refresh inbox.
The same exact lease binds both repositories, while each owner preserves its
existing generation, mutation and recovery rules. FilesStore and InboxStore are
selected verbatim from their individual overlays.

Read [composition behavior](../../contracts/behavior/connected-media.md) and
[the learning pattern](../../notes/patterns/producer-receipts-and-consumer-views.md).
An upload receipt confirms Files completion; it cannot prove Notifications
delivery. The UI never appends an optimistic notice or marks one read on navigation.
CLI 0.25.0 persists original Files bytes/commands through the separate private
journal; app restoration reads only and explicit retry/review remains required.
Inbox settings/read uncertainty still uses GET,
with no automatic resend. There is no new durable sync, OS push or retention policy.

With booted owned devices and an unused Debug loopback endpoint:

```sh
make connected-test CONNECTED_PLATFORM=both CONNECTED_SCENARIO=media
```

The copied runner seeds an empty account and an owned gallery image. Actual
screens upload three photos, recover one lost completion acknowledgement, read
the first notice, mute delivery and restore, then re-enable the third upload.
The final two notices must exclude the suppressed middle upload. Account
isolation, renewal, preview, recreation/process restore and sign-out are included.
The proxy independently checks one PUT/completion per distinct upload, receipt
settlement and no replay of read/preferences. The full scenario also runs the
base connected suite and individual Files/Inbox workflows in the combined app.

Inventory tests cover six native/backend copies, source equality, unchanged
feature owners, both fixture/owner closures and unsupported selection refusal.
Static checks alone do not establish native readiness; the independent execution
below supplies the separate native and backend evidence.

## CLI 0.26.0 notice destination verification — 2026-10-19

Two fresh both-platform copies under /private/tmp/mobile-notice-destination-026
were generated through the CLI and tested without source edits. The Go copy uses
example.com/notice-go/backend and port 18886; Rust uses notice-rust-api, the stable
api-server binary and port 18887. Both native platforms build/run against their
own selected backend. Current raw selected-source fingerprints were recomputed
after execution and match the generation metadata.

| Output | Installed identity | iOS media | Kotlin media | Source files |
| --- | --- | --- | --- | --- |
| notice-go | com.example.notice026go | 52 passed | 67 host, 11 device storage, both process stages passed | 799 |
| notice-rust | com.example.notice026rust | 52 passed | 67 host, 11 device storage, both process stages passed | 861 |

All selected results have zero failures/errors/skips. Each iOS run includes 51
app owner cases and the expanded media screen workflow. Each Kotlin host run has
11 Session, nine Services and 47 product owner cases, including four destination
cases. Swift host Session/Services and all 11 Data cases also pass. The expanded
adapter case exercises seven metadata admission scenarios; real API services
read pending/available metadata and deleted 404 under both backends.

Actual screens open the first delivered notice, resolve current metadata and
explicitly download verified bytes. Closing leaves one unread notice. Existing
lost read/preference replies still require authoritative readback, and three
distinct uploads retain one content/completion attempt each. After process
restore, account switches and renewal, a second Open races a separate authorized
client's delete and must display the actual backend's 404. The unread count
stays unchanged on both opens. The proxy asserts exactly two metadata reads,
one available result, one competing delete and one not-found, plus the existing
explicit read/preference attempt guards. This is not a synthetic payload-only
preview or an optimistic native notice.

| Output | Selected-source SHA256 |
| --- | --- |
| notice-go | 0d28af6cce7a3fc3f14cad6683bc6c97cdf7d1039c5a0bd4ba0d9e7f14c17faa |
| notice-rust | e44ee867d8d8020e468cba1a7e561eb079cd378da0625acdcd0132e1c0e6546a |

Reports are in each output's .cache/connected-ios/Results, .cache/connected-android
and native build/test-results directories. The parent evidence.json records the
counts, paths and fingerprints. CLI tests pass all 54 cases, including six copy
combinations and missing destination owner/screen/test refusal on both platforms.
Starter inventory and learning-note checks pass separately; they do not execute
native workflows.

Devices are iPhone 17 Pro/iOS 26.2 and an owned Android 16/API 36 read-only AVD.
The Android stages force-stop the app between them while retaining private
storage/Keystore in that VM. The APIs use ephemeral memory with owned SMTP
capture and loopback fault proxies. Runs use CONNECTED_SWIFT_DISABLE_SANDBOX=1
for the host SwiftPM manifest issue documented in the Files guide; the app
sandbox remains intact. Host disk pressure briefly blocked a documentation
write; cleaning only two unused disposable compiler caches restored space.
All four native executions completed successfully without source fixes or reruns.

The combined starter remains ready for this tested local baseline. This adds
in-app file navigation only: OS notification routing/delivery, URLs/deep links,
implicit account switching, persistent routes, background transfers, native
workflows across backend PostgreSQL restarts and public release remain separate.
The full-suite and durable recovery results below retain their original version
and do not become new full-suite/restart evidence merely because this run passed.

## CLI 0.23.0 initial combined verification

Independent outputs were generated on 2026-10-19 under
/private/tmp/mobile-media-023. All are untouched source copies; no checkout build
output was substituted. Go uses example.com/verified-go/backend and
example.com/verified-kotlin-go/backend; Rust uses the renamed cedar-api package
and stable api-server binary.

| Output | Installed identity | Native acceptance | Selected source files |
| --- | --- | --- | --- |
| verified-go | com.example.joinedgo023 | Swift full suite: 48 passed | 784 |
| verified-kotlin-go | com.example.joinedkotlingo023 | Kotlin: 53 host, 7 storage, all 16 full screen stages passed | 548 |
| verified-rust | com.example.joinedrust023 | Kotlin: 53 host, 7 storage, both media stages passed; Swift media: 41 passed | 846 |

All selected results have zero failures/errors/skips. Go's full suites
include the established Notes/identity/workspace/cache flows, the individual
Files/Inbox workflows and the joined upload-to-notice case in one composition.
The initial combined-go output passed its 41-case iOS media run but caught a
misplaced brace in the new Android instrumentation test. Host unit tests do not
compile that source set. The correction was made in the template, then the three
final outputs above were generated fresh; the earlier output is not final Android
evidence. The final Rust iOS media run also passes all 41 selected cases. Both
combined inventories were promoted only after these runs completed. Outputs
were generated while candidate; readiness metadata changes do not alter selected
program sources.

Recomputed selected-source content digests match each final metadata record:

| Output | SHA256 |
| --- | --- |
| verified-go | 4818a681dcbf5e5ced1c8891ae145de0cd70ce8dc1bffad663527265ff038232 |
| verified-kotlin-go | e533f5f05557b282bfbb8dcc6b1bf7ac8ecd2c5fbc7c56d2e68886e8b126773a |
| verified-rust | f9093769867e323b0bc0285e241519afd05ceabf9604765c3d5ecf7a6c593028 |

Reports remain in each output's .cache/connected-ios, .cache/connected-android
and native build/test-results directories. The devices are iPhone 17 Pro/iOS 26.2
and Android 16/API 36. Android uses an owned read-only AVD instance: force-stops
retain the app's private filesystem/Keystore during that instance. This does not
claim an emulator reboot, physical-device or public-release run. The APIs use
owned ephemeral memory, local SMTP capture and loopback fault proxies; no cloud
provider credentials, retained database or product debug endpoint is required.

## CLI 0.24.0 Files recovery follow-up

The canonical Files owner/UI reused by this selection now supports explicit
Keep/Forget review for terminal intent. It preserves Inbox recovery and sends no
notification or replacement file command. Focused Files scenarios and all app
owners pass in generated Kotlin Go and Swift/Kotlin Rust combined products;
Go Swift uses an independent Files-only selection of the same feature sources.
Read [the exact matrix, source fingerprints and limits](connected-files.md#cli-0240-recovery-verification--2026-10-19).
The full/joined-notice results above remain the CLI 0.23.0 evidence.

## CLI 0.25.0 durable Files follow-up

This composition now injects the separate PendingFiles journal while Inbox keeps
its independent readback policy. Canonical Files owners/UI remain reused verbatim.
Current combined Kotlin Go/Rust and Swift Rust copies pass durable Files restart
acceptance and all app owners; Swift Go exercises the same feature in a Files-only
copy. Read [the exact final matrix and host corrections](connected-files.md#cli-0250-durable-recovery-verification--2026-10-19).
The original full/joined-notice results above remain CLI 0.23.0 history.

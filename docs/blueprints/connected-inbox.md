# Optional connected inbox: CLI 0.21.0

Swift and Kotlin Daily + connected + inbox are **connected starter ready** with
either Go or Rust for the tested local baseline. Both backends use the same
native source and recovery policy.

Add account activity deliberately to either connected Daily starter:

```sh
mobile-foundry init canopy --platform both --with go-connected --with inbox --backend go \
  --app-id com.example.canopy
cd canopy
make go-dev
```

For Rust, select its matching connection and API:

```sh
mobile-foundry init cedar --platform both --with rust-connected --with inbox --backend rust \
  --app-id com.example.cedar
cd cedar
make rust-dev
```

Choose swift or kotlin instead of both for one native app. Backend choice does
not fork InboxStore or service admission. Activity shows current account notices,
unread count/filter, paging, mark-read and future media-delivery preferences.
Only completed uploads currently create notices; another authorized client can
upload through the copied backend's API. The generated native Files screen is
not part of this selection. SETUP.md documents endpoint/device configuration and
recovery. Existing connected products without inbox keep their Activity placeholder.

Read [behavior](../../contracts/behavior/connected-inbox.md), then
[current-state recovery](../../notes/patterns/inbox-refresh-and-setting-uncertainty.md).
Inbox writes have no receipts/CAS: an interrupted reply may follow a commit.
Controls require a fresh successful read, and restoration never resends a write.
Every session lease rebinds the repository; rows cannot survive sign-out or appear
under another account from a late result. No native inbox cache or OS permission
is selected.

Independent acceptance, completed 2026-10-19:

`/private/tmp/mobile-inbox-ready-tu4z9bfz/inbox-go` and
`/private/tmp/mobile-inbox-ready-tu4z9bfz/inbox-rust`, CLI 0.21.0,
app IDs com.example.inboxgo021 / com.example.inboxrust021. Go module is
example.com/inbox-go/backend; Rust API package is cedar-api.

The CLI suite passes 48 tests, including six native/backend copies, explicit
combination refusal, helper/fixture selection and base-profile independence.
Native source plans match between Go and Rust. Go's untouched output passes
47 selected Kotlin host tests, 36 iOS tests (including four new owner tests and
the actual inbox workflow), seven Android storage tests and both inbox screen
stages. All selected results have zero failures/errors/skips. The fault proxy
confirms one committed read/settings attempt per platform through restoration.
Rust's untouched output also passes the host checks and 41 full iOS cases,
including the established Notes/identity/workspace workflows and the added inbox.
All twelve Android screen stages and seven native storage cases pass. Proxy
verification additionally confirms pending Notes input/receipt recovery,
registration/workspace uncertainty and one inbox mutation attempt through restart.
The capability and both inventories were promoted only after those runs completed.

| Independent output | Selected source files | Kotlin host | iOS cases | Android storage / screen stages |
| --- | --- | --- | --- | --- |
| Go | 768 | 47 passed | 36 passed; inbox scenario plus hosted owners | 7 / 2 passed |
| Rust | 830 | 47 passed | 41 passed; full connected suite with inbox | 7 / 12 passed |

All selected results have zero failures/errors/skips. Recomputed selected-source
content hashes match both generated metadata records. No program source was
edited after final generation; no checkout build output was substituted. Test
reports remain in each output's .cache/connected-ios, .cache/connected-android
and native build/test-results directories. The local test devices were iPhone
17 Pro/iOS 26.2 and Android 16/API 36. Android used an owned read-only emulator
instance; app force-stops preserve its private filesystem/Keystore within that
instance. This does not claim a full emulator reboot or physical-device acceptance.

From a generated product with installed tools and booted simulators/emulators:

```sh
make connected-test CONNECTED_PLATFORM=host
make connected-test CONNECTED_PLATFORM=both CONNECTED_SCENARIO=inbox
```

The copied driver owns a fresh memory API and temporary users, creates 25 tiny
uploads through normal Files routes and waits for real Notifications delivery.
It withholds the first successful mark-read and media-disable responses behind
503 replies. Apps must refresh rather than replay; the proxy checks one attempt
and one commit per operation after account changes, renewal and process restoration.
The full scenario also runs the established Notes/identity/workspace workflows.
No provider keys, real user data, retained database or product debug endpoint is used.

The first narrow-copy compile caught an omitted RemoteServiceAdmission helper
that the full catalog package included implicitly. Both inventories now require
that helper, and clean products were regenerated. The acceptance proxy also
needed explicit PUT forwarding for upload bytes/preferences. These corrections
were investigated in disposable output; final acceptance uses fresh generation
with no program edits or substituted build artifacts. Inventory copy tests alone
cannot prove a selected Swift/Kotlin source set compiles.
Public deployment, release-device performance, background sync, connected database
restart and OS notification delivery remain separate acceptance.

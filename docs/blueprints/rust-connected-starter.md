# Rust-connected native products: CLI 0.20.0

Daily can explicitly select the Rust backend while keeping the existing native
ports, composition and lifecycle policy. The native source closure is byte-for-byte
the same as Go-connected; the generated product owns only the selected backend.
Swift and Kotlin are **connected starter ready** for this tested local baseline.
Read [behavior](../../contracts/behavior/rust-connected-starter.md) and
[why a composition owner matters](../../notes/patterns/connected-products-and-composition.md).

After installing the local CLI, run from the parent directory for a new project:

```sh
mobile-foundry init canopy --platform both --with rust-connected --backend rust \
  --app-id com.example.canopy --rust-package canopy-api
cd canopy
make rust-dev
```

Choose swift or kotlin instead of both for one app. Account provides Create
account and explicit sign-in; Library provides scoped remote Notes. SETUP.md
documents registration, workspace management, credentials, live reads, pending
commands and saved read pages. It also contains Rust PostgreSQL/operations
commands. GoSessionAuthority remains the shared HTTP wire adapter's source name;
it does not require a Go process. Rust choice does not copy a Go backend.

Public --api-base-url admission and native Release HTTPS policy are unchanged.
Loopback Debug defaults to port 8080; use another unused loopback port for local
acceptance and forward that exact port to Android with an owned ADB mapping.
For a physical phone use reachable HTTPS. Keep release signing and private SMTP/
provider credentials deliberate. Backend-only and co-located UI-only selections
retain their existing behavior; local-data and other shells are not connected
implicitly.

Independent output, generated 2026-10-18 and exercised through 2026-10-19:
`/private/tmp/mobile-rust-connected-tmyy73rh/canopy-connected`, display Canopy Rust,
app ID com.example.canopyrustconnected, Rust API package canopy-api. It was copied
while rust-connected was candidate; promotion requires actual generated screens.
The capability was promoted only after both platforms completed the checks below.

Observed: all 44 CLI cases and starter inventory checks pass. Both host
transport suites pass against the generated Rust process: sessions/rotation/
revocation, registration, workspace creation/CAS/isolation, Notes receipts and
replay, recovery/verification/password changes, local SMTP and native WebSockets.
SQLite pending/read-cache tests also pass. Kotlin host XML reports 41 selected
tests with zero failures/errors/skips. Generated iOS full acceptance passes 36
tests with zero failures/skips on iPhone 17 Pro, iOS 26.2. Its actual relaunches
and the fault proxy verify secure restore, receipt lookup/exact-input retry,
registration duplicate/lost replies and workspace conflict/uncertainty readback.
Android full acceptance passes seven native Data tests and all ten separate
instrumentation stages, each executing once without failures/skips. Force-stops
between stages verify Keystore credentials and the exact pending command across
killed app processes, separately from Activity recreation. The final proxy checks
confirm one commit, receipt lookup, exact-input retry, no automatic registration
resend and workspace conflict/lost-reply readback in both apps.

Observed tools: Rust/Cargo 1.86.0, Xcode/iOS Simulator 26.2, JBR 21.0.6 and Android
16/API 36 (API36_Test). The Android acceptance used a read-only emulator instance
to avoid an existing AVD lock; its private filesystem/Keystore persisted between
app force-stops. No full emulator reboot or physical-device claim is made.
All selected test results have zero failures/errors/skips. The 815-file selected
source digest matches the accepted output after the final source changes;
only generated guide projection was subsequently refreshed for its backend label.
Reports remain in the output's .cache/connected-ios and .cache/connected-android
and Kotlin product/core:data build directories. No source-checkout build output
was substituted for the product's own artifacts.

From the generated product, with installed tools and booted test devices:

```sh
make connected-test CONNECTED_PLATFORM=host
make connected-test CONNECTED_PLATFORM=both
```

The driver uses the product metadata to choose its copied API. Rust builds use
--locked, the selected package and the stable api-server binary, with an explicit
output-owned target directory. Hostile inherited database/provider/metrics/proxy
configuration is cleared before startup. Go is not used on the Rust build path.
Test methods and GO_SESSION_TEST_BASE retain their original shared-contract
names; actual process selection provides the backend evidence.

Native UI cases cover sign-in, account isolation, CRUD/renewal, Keychain/Keystore
restoration, exact-input pending writes after process restart, read-cache outage
and removal, registration duplicate/lost replies, workspace CAS/conflict/lost
replies and live read/security forms. Neither fixture success nor a build alone
establishes those workflows. Current acceptance is memory-backed; standalone
Rust PostgreSQL/operations evidence stays [separate](rust-starter.md).
Public hosting, release devices, live cloud delivery, automatic sync and native
Files/inbox/push/queue UI remain later selected capabilities.

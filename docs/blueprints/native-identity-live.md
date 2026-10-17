# Native recovery and live account reads

CLI 0.15.0 adds these capabilities to Daily + go-connected. Reusable ports/adapters
live in Session; transient forms and reconnect/read policy live in product overlays.
Read [the behavior boundary](../../contracts/behavior/native-identity-live.md).

## Read the source

1. Swift [IdentityRecovery](../../frontend/swift/packages/FoundrySession/Sources/FoundrySession/IdentityRecovery.swift)
   and Kotlin [IdentityRecovery](../../frontend/kotlin/project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRecovery.kt).
2. Swift [live port/URLSession](../../frontend/swift/packages/FoundrySession/Sources/FoundrySession/AccountLiveHints.swift)
   and Kotlin [live port/OkHttp](../../frontend/kotlin/project/core/session/src/main/kotlin/dev/mobilefoundry/session/AccountLiveHints.kt).
3. Product [Swift security owner](../../templates/overlays/go-connected/swift/IdentitySettingsModel.swift)
   and [Kotlin security owner](../../templates/overlays/go-connected/kotlin/IdentitySettingsModel.kt).
4. [Swift composition](../../templates/overlays/go-connected/swift/ConnectedSessionModel.swift)
   and [Kotlin composition](../../templates/overlays/go-connected/kotlin/ConnectedSessionModel.kt).
5. [Shared vectors](../../contracts/fixtures/native-lifecycle/hints.json),
   [owned driver](../../scripts/connected_acceptance.py) and
   [local mail fixture](../../scripts/local_mail_capture.py).

## Use

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Account → Account security / recover access contains the forms. Configure private
backend IDENTITY_MAIL_CONFIG to receive codes; generic reset acceptance alone does
not promise delivery. Verify an address first to enable recovery. Login remains
separate from verified email. Contact changes, registration UI and richer auth are
separate. Library shows connection status/Reconnect and refetches after another
client changes the same authorized account.

Follow SETUP.md for registration/device mapping. Release needs reachable HTTPS.

## Verify independently

Stop any development API at the selected endpoint first. From an independent copy:

```sh
make connected-test CONNECTED_PLATFORM=host
make connected-test CONNECTED_PLATFORM=swift
make connected-test CONNECTED_PLATFORM=kotlin
```

Host checks use real HTTP/sockets and locally captured SMTP codes to verify contact,
password reset/change and session revocation. Native UI checks exercise external
live writes, actual verification/change/reset forms and subsequent sign-in. Existing
pending receipt/process-restoration checks remain required. The driver checks
executed results and removes only owned services/mappings.

Unit tests additionally cover route/purpose/input admission, strict scoped hints,
cross-deployment refusal, coalescing without replay and interrupted password change
with a late reply. Independent execution is recorded below. Local
Debug checks do not establish external delivery, signing or device performance.


## Observed verification — 2026-10-17

CLI 0.15.0 generated `/private/tmp/mobile-native-foundations-2o0f4kjn/native-foundations`,
installed identity `com.example.nativefoundations`, module
`github.com/example/native-foundations/backend`. The copy was not patched after
generation. Its own `make connected-test CONNECTED_PLATFORM=both` passes:

- Real URLSession/OkHttp session/account and Notes acceptance, plus each platform's
  actual ready/changed socket frames, captured verification/reset email codes,
  contact readback, password change/reset and refusal of revoked access.
- Eleven hosted iOS state/presentation checks plus two full UI cases on iPhone 17
  Pro/iOS 26.2: **13 passed, zero failures/skips**. The cases include external write
  → live hint → HTTP read, verification/change/reset forms and recovered sign-in.
- Four install-once Android instrumentation stages on Pixel 9 Pro/Android 15:
  **write, restore, retry and security each execute once without failure/skip**.
  Seven product state tests pass, including coalesced reads without replay and
  interrupted password change with a late response.
- Existing owned proxy assertions still observe one original commit, no automatic
  replay, original command IDs/bytes and explicit receipt recovery after process
  termination on both platforms. Keychain/Keystore restoration remains real native
  storage, separately from Activity recreation.

Source Session suites and all 32 CLI cases pass; notes/inventory checks and
`git diff --check` pass. The owned driver removes its services and mappings.
The backend in this run is an owned memory API; PostgreSQL durability and portable
Docker recovery retain their separate 0.14.0 evidence.

Earlier UI iterations exposed iOS AutoFill grouping verification/code/new-password
fields into an invalid password form. Distinct verification/change modes and a
privacy-sensitive OTP TextField resolve that interaction; focus identities alone
were insufficient. Acceptance also handles the real system strong-password prompt.
Android's final re-sign-in now supplies both login and recovered password explicitly,
rather than relying on a retained login draft after the session transition. Focused
debug copies informed the fixes; the final evidence above uses fresh extraction.

Daily + go-connected remains **connected starter ready**, now through CLI 0.15.0
for this scope. Other shells/catalog screens, registration/contact-change UI,
read caching/automatic sync, multi-replica fanout, external SMTP/push delivery,
production rollout and release/device performance remain deliberate extensions.

# Native registration and onboarding

CLI 0.17.0 adds native registration to Daily + go-connected. The reusable port is
in Session; transient forms and owner policy are in product overlays. The backend
registration/sign-in/Account bootstrap contracts are unchanged. Read
[the native contract](../../contracts/behavior/native-registration.md) and
[why the outcomes stay separate](../../notes/patterns/registration-is-not-session-issuance.md).

## Source reading order

1. [Swift command/port/HTTP](../../frontend/swift/packages/FoundrySession/Sources/FoundrySession/IdentityRegistration.swift)
   and [Kotlin command/port/HTTP](../../frontend/kotlin/project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRegistration.kt).
2. [Shared admission inputs](../../contracts/fixtures/native-registration/inputs.json).
3. [Swift owner](../../templates/overlays/go-connected/swift/RegistrationModel.swift)
   and [Kotlin owner](../../templates/overlays/go-connected/kotlin/RegistrationModel.kt).
4. [Swift form](../../templates/overlays/go-connected/swift/RegistrationView.swift)
   and [Kotlin form](../../templates/overlays/go-connected/kotlin/RegistrationScreen.kt).
5. [The copied acceptance driver](../../scripts/connected_acceptance.py).

## Use

```sh
mobile-foundry init canopy --platform both --with go-connected --backend go \
  --app-id com.example.canopy --go-module github.com/your-org/canopy/backend
cd canopy
make go-dev
```

Follow generated SETUP.md for the public API endpoint and native device mapping.
Open Account → Create account. After success, Continue to sign in prefills only
the canonical login; enter the password again. First sign-in opens Personal.
Add a verified recovery email through Account security after signing in.

If creation is unconfirmed, try signing in before submitting again. The form has
no retry loop or durable password record. Duplicate registration cannot replace
the existing password. Other shells and catalog examples keep their own selection.

For independent native acceptance, stop the development API first, then run in the
generated copy with an available simulator/emulator:

```sh
make connected-test CONNECTED_PLATFORM=both
```

The driver owns loopback API/mail/proxy processes. It creates unseeded identities
through the actual forms, verifies newly issued credentials survive process
restart, refuses duplicates, and loses a committed registration reply before
explicit sign-in. Proxy counters assert original commits without automatic replay.
The test control is outside both app and backend runtime code.

## Observed verification — 2026-10-17

CLI 0.17.0 generated the untouched independent output at
`/private/tmp/mobile-registration-verified-yz2nc6d9/registration-verified`, identity
`com.example.registrationverified`, module
`github.com/example/registration-verified/backend`. Its own
`make connected-test CONNECTED_PLATFORM=both` passes. Sixteen selected registration
runtime/test/fixture files match source; this final copy received no source
patches after generation.

- iPhone 17 Pro/iOS 26.2: **29 checks passed, zero failures/skips**, comprising
  twenty-five hosted state/presentation checks and four actual UI scenarios.
  The new registration scenario exercises local field feedback/password clearing,
  unseeded registration, explicit sign-in, Personal/Notes, process restart,
  duplicate refusal with a different password, and original-password sign-in.
- Pixel 9 Pro/Android 15: **eight UI stages each pass once without failure/skip**:
  write, restore, retry, security, cache-write, cache-restore, registration-create
  and registration-restore. Install-once force-stops preserve native private
  credentials and data between processes; the new user restores its Personal/Note.
- Both UI platforms lose a server-committed registration reply, show uncertainty,
  leave the password blank and recover through explicit sign-in. Owned proxy
  counters observe **two attempts/one commit for normal plus duplicate**, and
  **one attempt/one commit for the lost-reply identity**. No implicit resend or
  replacement of the original password occurs.
- **Four registration boundary cases per platform** cover shared input admission,
  normalization/Unicode bounds, redaction, strict replies, one public attempt and
  cancellation. Real native Go registration checks additionally prove separate
  sign-in/Personal and unchanged original credentials after duplicate refusal.
- **Six new registration-owner cases per platform** cover local/session gates,
  no implicit grant/vault write, double submit, uncertainty versus duplicate,
  cancellation and late session-epoch publication. Swift's cases run within its
  hosted checks; Android's **21 product-state cases** include these six plus the
  prior fifteen Notes/session cases, all without failure/skip.
- **Seven selected native SQLite tests per platform** still pass. Existing
  identity/mail/socket, read-cache outage and pending-command one-commit recovery
  scenarios remain required and pass. Kotlin host tasks rerun without test-cache
  reuse, against this run's owned Go API.

The final log is `/private/tmp/mobile-registration-verified-final.log`. The iOS
bundle is `.cache/connected-ios/Results/connected-1792266785461597000.xcresult`
inside the copy. Android stage receipts remain under `.cache/connected-android`,
with state XML under product/build/test-results and native storage XML under
core/data/build/outputs/androidTest-results. All 32 CLI cases, inventory/notes
checks and `git diff --check` pass. The driver removes its API/mail/proxy and owned
reverse mappings; the test devices are closed afterward.

Daily + go-connected remains **connected starter ready**, now through CLI 0.17.0
with native registration. Go retains its separate PostgreSQL/operations baseline.
Other shells/catalogs, contact change, invitations/roles, required-contact policy,
account deletion, MFA/OIDC/passkeys, cloud delivery and release/device acceptance
remain deliberate product extensions. Source admission checks are not a password-
strength or whole-identity-completeness claim.

The additional Swift integration case initially exceeded the API's two-concurrent
password-computation admission budget when three scenarios ran together. The
driver now runs registration integration separately; the API budget is unchanged.
This is test scheduling, not a relaxation or automatic retry in the product.

Review kept the seam's profile ID aligned with Principal's provider-independent
admission; the selected HTTP adapter owns Go's stricter UUID wire rule. A separate
review added displayed field-specific errors, so local validation messages do not
remain hidden behind a generic banner. Final native acceptance asserts the
password-specific feedback as well as clearing the rejected draft.

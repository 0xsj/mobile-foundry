# Installation registry construction

Notifications owns the session-bound installation registry. Swift/Kotlin now
supply typed registry ports, secure installation identity and lifecycle owners,
plus catalog forms to register, rotate, explicitly rebind and unregister a target.
This capability manages metadata; OS token collection and push delivery are separate.

Read [behavior](../../contracts/behavior/installations.md),
[HTTP](../../contracts/http/installations-v1.md),
[credential v2](../../contracts/capabilities/identity-credential-v2.md) and
[the learning walkthrough](../../backend/go/notes/modules/internal/notifications/installations.md).

## Run

Use AUTH_MODE=identity make dev in backend/go. PostgreSQL users must explicitly
run make migrate before starting the upgraded API; the existing Notifications
schema now includes 0002_installations.sql. API startup verifies, never applies SQL.
No new environment variables or provider credentials are required for metadata.

Register/sign in a development user through the existing Identity HTTP API, then
supply its access token. Generate a fresh installation UUID, registration UUID and
32-byte random installation key. Keep the key out of URLs/logs; the native catalogs
keep it in a deployment/app-partitioned vault. X-Installation-Key
is independent of the Bearer credential and the provider token. POST the registry
payload from the HTTP contract. Save only sanitized response metadata alongside
your secure local installation identity. Updates use a new registration ID and the
current ETag. Uncertain writes require current-state readback, not blind retry.

The APNs/FCM labels and application key are local routing declarations. This run
does not attest a real token or select provider endpoints/credentials. Native
catalogs expose the connected inbox and the metadata registration form below.
No system permission prompt or automatic OS token collection is selected. Sign-out through Identity
asynchronously retires matching session registrations; local-only credential
removal cannot produce that server fact by itself.

## Verification

Runnable (repository root):

```sh
make go-check go-test
make -C backend/go smoke
make -C backend/go integration-test POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin
make cli-test starter-check notes-check
```

The local database mode needs PostgreSQL binaries. Default integration-test uses
an owned disposable Docker database. Tests never reset the retained development
volume. Provider conformance covers twelve-way create contention, proof refusal,
CAS rotation/rebind, late cleanup/revocation, tombstone admission, target uniqueness,
expiry reclamation, settled unregister and cancellation. HTTP checks include v1/v2
capability compatibility and caller policy, sanitized metadata, renewal and real
sign-out cleanup. PostgreSQL forces installation/tombstone/inbox failure and checks
that no partial retirement survives. Its process test blocks revocation, kills
the API, then observes recovered cleanup and raw-token erasure.

CLI 0.7.0's independently generated device-api passes its own full Go checks/race/
smoke, disposable PostgreSQL 14.24 rollback/SIGKILL recovery and generated Make-runner
proof/rebind/unregister acceptance. Go remains standalone starter ready. Actual
source/generated results are recorded in
[starter readiness](../STARTER-READINESS.md). Backend evidence is separate from native storage/HTTP evidence below and does
not establish cloud delivery or production retention/failover.

## Native catalog workflow

Open Catalog → Go identity and accounts, connect to the owned Go API, sign in,
then scroll to Device registration. Credentials selection also chooses Device
secure proof storage or explicit process-only Memory storage. Wait for the
initial read, enter an opaque registry probe address, then register. Replace the
token with a new explicit intent, refresh the current state, or unregister.
iOS exposes Sandbox/Production APNs routes; Android uses the current application
ID with FCM/Production. These labels do not attest a real provider token.

The vault creates/persists a UUID and cryptographic 32-byte key before any registry
request. Its app/deployment namespace stays independent of user/account. Device
uses Keychain ThisDeviceOnly/non-synchronizing storage or Keystore AES-GCM plus
AtomicFile in no-backup storage. Corrupt records and missing encryption keys fail
without implicit reset. Memory lasts only in the current application process.
Neither adapter offers an automatic auth-driven clear operation.

The shared InstallationController is UI-owned. Every exact lease change cancels
and invalidates old completions, clears its record and reads via a new injected
registry client. Renewal retains the server binding; account changes retain the
physical key. Sign-out/close unbinds native work without queuing a stale DELETE.
Successful Identity revocation eventually retires the matching server target.
An offline/failed revoke does not promise immediate server retirement.

Mutation failure requires a fresh GET before another write. No automatic replay,
durable queue, immutable receipt or last-write history is claimed. The form clears
its token draft on submission and lease changes. Private keys/targets never enter
controller snapshots or diagnostic formatting. Unexpected defects reach an
app-injected reporting hook and a generic public failure; expected failures remain
values. Reusable UI is passive and contains no provider or session SDK logic.

Runnable (repository root; iOS simulator and Android emulator available):

```sh
make http-test
make go-native-test
make installations-ui-test
make starter-check notes-check
```

Observed 2026-10-17: both catalogs build; the shared response corpus and controller
uncertainty/storage/late-lease cases pass on both platforms. URLSession/OkHttp
against an owned Go API pass create acknowledgement loss/readback, explicit
session handoff, unchanged binding through renewal, old-session unregister
refusal, settled unregister, token replacement and asynchronous sign-out retirement.
The owned API runner also checks logs for test password/proof/target prefixes.
Two iOS Keychain and two Android Keystore cases execute without failures/skips on
iPhone 17 Pro/iOS 26.2 and API36_Test/Android 16. They cover reopen, concurrent
creation, deployment separation, auth-clear independence and corruption
preservation; Android additionally verifies lost-key refusal. Android runs in an
emulator overlay; no physical secure-hardware or old-OS runtime claim follows.

Native evidence: the Xcode result
`.cache/ios/Logs/Test/Test-FoundryCatalog-2026.10.17_13-42-35-+0300.xcresult`
reports two passed, zero failed and zero skipped. Android's connected XML for
`dev.mobilefoundry.catalog.installations.InstallationVaultTest` reports two tests,
zero failures/errors/skips. A prior Xcode rebuild hit an interrupted generated-file
read (I/O code 4); the focused Make run above rebuilt and passed afterward.

The Android catalog enables desugar_jdk_libs 2.1.5 for java.time/Base64 on its
minSdk 24 baseline. A current-OS emulator pass is not old-OS device acceptance;
see [the substrate note](../../frontend/kotlin/notes/substrate/java-api-desugaring.md).

APNs collection requires app signing/capability setup and the registration
callback described by [Apple](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns).
FCM collection requires configured Firebase client integration, initial token
retrieval and onNewToken handling described by
[Firebase](https://firebase.google.com/docs/cloud-messaging/android/get-started).
Neither configuration, callback integration nor provider sender is added here.
There is no permission flow, OS token attestation, background registration retry,
notification-open routing, device push acceptance or catalog-gesture acceptance.

These sources are not yet selected into a generated connected CLI combination;
existing native local starters and standalone Go readiness remain unchanged.
Read [the shared pattern](../../notes/patterns/installation-identity-and-current-state-reconciliation.md),
then the native Services and app walkthroughs for actual source/check links.

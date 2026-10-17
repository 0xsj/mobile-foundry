# Session walkthrough

core:session owns credential lifecycle and account-access admission behind interchangeable authority and storage ports.

## Origin and reading order

Session foundation, 2026-10-10. Start with [behavior](../../../../../../../contracts/behavior/session.md)
and [construction](../../../../../../../docs/blueprints/session.md).

1. [Build](../../../../../project/core/session/build.gradle.kts): Android/Kernel ownership and JVM fixture tests.
2. [Models](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/SessionModels.kt): private factories/copy admission, redacted secret classes, versioned UTF-8 records, snapshot and lease.
3. [Ports and memory](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/SessionPorts.kt): result boundaries and mutex-owned vault.
4. [Controller](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/SessionController.kt): one UI executor, StateFlow, coroutine generation and expiry/clear barriers.
5. [Keystore](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/KeystoreCredentialVault.kt) and [demo authority](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/DemoSessionAuthority.kt): native persistence versus fixture lifecycle.
6. [Shared fixture](../../../../../../../contracts/fixtures/session/admission.json), [JVM tests](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/SessionTest.kt) and [native vault tests](../../../../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/session/KeystoreCredentialVaultTest.kt).
7. [Catalog consumer](../../app/README.md#identity-and-session).

## Walkthrough and example

Factories admit bounded values and normalize login with Locale.ROOT. Passwords
are preserved. ConsistentCopyVisibility prevents private data-class factories
being bypassed by public copy. Secret-bearing grant/credential/command classes
override toString; direct property logging remains the caller's responsibility.
Private record decoding checks version, strict UTF-8 and domain factories;
credentials persist scope and refresh token only.

Restore marks busy before scheduling. It loads, verifies issuer, renews, checks
scope/expiry, saves rotation and then publishes active identity/account. Controller
methods run on the caller's single UI executor, with Dispatchers.IO confined to
the native vault. The controller owns a read-only StateFlow, not Compose types.

```kotlin
// Conceptual: a feature rejects results from an old account or session epoch.
val lease = session.lease ?: return
val result = loadAccountFeature()
if (!session.isCurrent(lease)) return
apply(result)
```

Each suspension verifies generation and coroutine activity. A lazy Job is assigned
before start so immediate execution cannot leave a completed request handle.
Sign-out cancels/join-waits its predecessor before clearing, then revokes. A remote
failure leaves a signed-out warning; failed local clear stays blocked/incomplete.
Shutdown permanently closes the owner without deleting saved credentials.

## Evidence and limits

All thirteen JVM session checks pass: shared factories/records, restore/rotation/
owner leases, expiry/account replacement, invalid command no-IO, transient versus
rejected renewal, issuer/scope mismatch, storage failure, expired grants/revoke
warnings, uncancelable save ordering, external cancellation, demo rotation and
late defects after shutdown/cancellation. Deferred gates and NonCancellable fixture work
reproduce races deterministically. Three actual Keystore checks and two native
gallery checks pass; the app handoff records consumer behavior.

This is one active namespace writer with a shared device vault, not cross-process
locking. A real provider, account data migration, automatic
renewal/retry and device-lock/hardware budgets remain separate work.
Read [Keystore substrate](../../../../substrate/keystore-encryption-and-atomic-credentials.md),
[copy mechanics](../../../../language/kotlin-sealed-ui-states-and-data-classes.md#admitted-data-and-copy-visibility)
and [scope lifetime](../../../../../../../notes/patterns/session-scope-and-credential-lifetime.md).
Next: how should a real authority expose uncertain rotation, and which in-flight
feature work should be cancelled when its lease becomes stale?

## Authenticated HTTP

The account-access batch, 2026-10-10, adds
[AuthenticatedHTTPClient](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/AuthenticatedHTTPClient.kt)
and the module's HTTP dependency. core:http remains independent of Session.
The suspend adapter is called on the controller's single UI executor and binds
one lease; OkHttp callbacks do not become the session's publication executor.

## Go authority and account directory — 2026-10-13

Read [GoSessionAuthority](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/GoSessionAuthority.kt),
[wire admission](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/GoSessionWire.kt)
and [HTTPAccountDirectory](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/HTTPAccountDirectory.kt).
An admitted URI becomes a deployment-bound SHA-256 issuer; the protocol label is
checked separately. UTC/POSIX-style SimpleDateFormat instances keep API-24 date
parsing independent of the user's locale. Microsecond ordering is checked before
flooring access expiry to milliseconds; malformed surrogate/name/token values fail.

The suspend checkpoint is a controller-supplied function, not a vault injected
into the authority. After admitting rotation and principal, the Go adapter waits
for that callback before reading the stored account ID. Default interface behavior
preserves existing one-step authorities. Known rotation and confirmed persistence
are separate; a failed save stops IO and retry first flushes the pending credential.
After a successful checkpoint, transient Account failure retains current credentials
without publishing access. Missing renewal responses remain uncertain.

Conceptual example: restore a workspace credential while the adapter's initial
selection is Personal; renewal still reads the stored workspace, never bootstraps
a replacement. AccountSelectingAuthority performs an explicit guarded scope change,
and the directory reads only through its injected lease-bound HTTP client.

[Five checks](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/GoSessionTest.kt)
consume shared fixtures and exercise failure recovery/privacy. The separate
[OkHttp/Go check](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/GoNativeSessionTest.kt)
uses runBlocking with real time/socket callbacks, not virtual test time that can
advance an HTTP timeout before the native response arrives. `make go-native-test`
owns its server; the ordinary suite skips that explicitly unconfigured case.
Device Keystore/UI observations are recorded separately in the app notes.

Related: [checkpoint pattern](../../../../../../../notes/patterns/deployment-authorities-and-rotation-checkpoints.md)
and [behavior](../../../../../../../contracts/behavior/go-native-session.md).
Next: remote account data and independently extracted connected-product acceptance.

Construction requires HTTPS unless explicit loopback HTTP is enabled. Admission
checks cancellation/current lease, owns Authorization, rejects caller overrides
and invalid Bearer syntax, then copies all request options. After the attempt,
the same lease gates values and thrown Exceptions. Cancellation propagates;
stale non-cancellation exceptions become scope-changed values. A 401 neither
mutates the credential/controller nor renews/replays the attempt.

Example: start an Alex request, switch accounts during suspension and reject its
old result. The server may already have accepted it; this is publication admission,
not a rollback guarantee.

[Tests](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/AuthenticatedHTTPTest.kt)
cover shared Bearer fixtures, request options/owned header, HTTPS/path admission,
401/no mutation, expiry/renewal/sign-out, late success/failure/exception and
cancellation. Actual OkHttp plus loopback MockWebServer verifies the header and
single redirect refusal. All twenty JVM cases pass, including thirteen original
lifecycle cases. The real-network test uses runBlocking and a bounded request
wait; virtual time remains for deterministic controlled races. No real provider,
TLS certificate exchange or hardware behavior is established.

Read [native transport/digest mechanics](../../../../substrate/message-digest-and-authenticated-okhttp.md),
[shared ownership](../../../../../../../notes/patterns/account-partitions-and-leased-work.md)
and [behavior](../../../../../../../contracts/behavior/account-access.md).
Next: how should a connected authority expose issuer/audience and replay policy?


## Identity/live follow-up — 2026-10-17

Claim: recovery, credential ownership and live invalidation need separate ports
because their authority and retry semantics differ. Origin: Daily connected slice.
Read [IdentityRecovery](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRecovery.kt), [AccountLiveHints](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/AccountLiveHints.kt) and [behavior](../../../../../../../contracts/behavior/native-identity-live.md).

The recovery HTTP adapter separates public operations from an exact-lease client.
Purpose/input checks happen before I/O; contact projection rejects wrong types or
extra fields. No new credential format or implicit renewal is introduced. Live
admission derives the deployment ID from the canonical API base before obtaining
its bearer, then checks the current lease for every hint. Socket transports own
cancellation and refuse cookies/redirects. Known transport refusal is a public
failure; cancellation and unexpected defects preserve their separate paths.

Conceptual: a password reset revokes every old session, while a live connection
merely requests a read and never grants access or confirms a pending write.

Shared JSON vectors and routing tests cover admission; real native HTTP/socket/
local-SMTP checks cover verification, reset/change and revoked access. App lifecycle,
form clearing and reconnect policy remain product-owned. The default catalog
session example is not changed by selecting new generated product screens.
See [independent evidence](../../../../../../../docs/blueprints/native-identity-live.md).
Next: adapt another provider only after checking its actual challenge/session and
subscription semantics; interface conformance alone is insufficient.

## Native registration — 2026-10-17

Claim: a public registration port can confirm a profile without gaining authority to issue a session or write credentials. Origin: connected onboarding slice. Read [command/port/HTTP](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRegistration.kt), [boundary tests](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/IdentityRegistrationTest.kt) and [shared admission vectors](../../../../../../../contracts/fixtures/native-registration/inputs.json).

RegisterIdentity has private construction and redacted debug output. Admission normalizes only the documented login/name fields, then preserves password bytes. HTTPIdentityRegistration sends one public request, admits an exact canonical profile reply, and never calls SessionAuthority. Go provider acceptance additionally proves duplicate registration cannot replace the original name/password.

Gotchas: duplicate login is not ownership proof; cancellation is not rollback; this endpoint has no command receipt/idempotency key. Never put registration passwords into the Notes journal/cache or replay them on restore. The native ASCII login subset is deliberate and tested, rather than inferred from platform lowercase/trim defaults.

Actual verification and versions are recorded in [construction/evidence](../../../../../../../docs/blueprints/native-registration.md). Continue with [profile versus session](../../../../../../../notes/patterns/registration-is-not-session-issuance.md). Next: where should a product place invitations, verified-contact requirements and approval before granting an account?


## Owner workspace follow-up — 2026-10-17

Claim: metadata editing uses versioned observations without silently changing
selected data scope. Origin: the CLI 0.18.0 workspace slice. Read
[construction and independent evidence](../../../../../../../docs/blueprints/native-workspaces.md), then
[the source](../../../../../project/core/session/src/main/kotlin/dev/mobilefoundry/session/AccountManagement.kt) and
[the focused checks](../../../../../project/core/session/src/test/kotlin/dev/mobilefoundry/session/AccountManagementTest.kt).

AccountName counts Unicode scalars and retains exact normalization. The provider
read/create/rename port is swappable; its Go adapter adds UUID/owner/strong-ETag
and exact command confirmation. Product owners require a fresh read before rename,
block queued double submissions, and retain interrupted-create uncertainty before
cleanup. A fresh page plus explicit duplicate-risk choice enables a new create;
it does not prove the earlier command's outcome. Rename changes display metadata,
not a lease, vault or pending Notes checkpoint. Drafts/warnings are process-local.

Conceptual flow:

```text
current observation → If-Match rename → conflict → fresh read → user review
```

Six owner cases per native platform cover input, duplicate submit, read gates,
uncertain creation, generation replacement and exact-lease rejection. Boundary
and actual Go port tests establish their separate guarantees; device evidence and
limits are recorded in the construction guide. See
[current state versus command receipts](../../../../../../../notes/patterns/current-state-is-not-a-command-receipt.md). Next: should this product
require durable Account command receipts or membership/invitation policy?

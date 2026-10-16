# Session walkthrough

FoundrySession owns credential lifecycle and account-access admission behind interchangeable authority and storage ports.

## Origin and reading order

Session foundation, 2026-10-10. Start with [behavior](../../../../../../contracts/behavior/session.md)
and [construction](../../../../../../docs/blueprints/session.md).

1. [Manifest](../../../../packages/FoundrySession/Package.swift): Kernel dependency, Security linkage and native test target.
2. [Models](../../../../packages/FoundrySession/Sources/FoundrySession/SessionModels.swift): factory admission, secret redaction, credential version, public snapshot and owner-bound lease.
3. [Ports and memory](../../../../packages/FoundrySession/Sources/FoundrySession/SessionPorts.swift): async result boundaries and serialized vault.
4. [Controller](../../../../packages/FoundrySession/Sources/FoundrySession/SessionController.swift): MainActor publication, task generation, expiry gate and save/clear barriers.
5. [Keychain](../../../../packages/FoundrySession/Sources/FoundrySession/KeychainCredentialVault.swift) and [demo authority](../../../../packages/FoundrySession/Sources/FoundrySession/DemoSessionAuthority.swift): native persistence versus fixture lifecycle.
6. [Shared fixture](../../../../../../contracts/fixtures/session/admission.json), [package tests](../../../../packages/FoundrySession/Tests/FoundrySessionTests/SessionTests.swift) and [native Keychain tests](../../../../apps/FoundryCatalog/Tests/SessionKeychainTests.swift).
7. [Catalog consumer](../../apps/FoundryCatalog/README.md#identity-and-session).

## Walkthrough and example

Command factories normalize login while preserving password. Principal/account
IDs are bounded ASCII identifiers; display names are bounded Unicode values.
Tokens are bounded printable opaque values. Credential decoding reconstructs
admitted values instead of trusting synthesized Codable domain constructors.
Only refresh credentials and scope metadata persist. Secret-bearing values
provide redacted description/debugDescription, but their owners must still
avoid logging explicit secret properties.

Restore admits one busy operation, closes prior leases, loads a saved record,
checks its authority, renews and validates the returned scope/expiry. A successful
vault save precedes public identity/account publication. Every suspension is
followed by generation/cancellation admission. Invalid drafts perform no IO;
valid replacement clears the previous credential before requesting a new grant.

```swift
// Conceptual: a feature gates a result with the lease it captured before work.
guard let lease = session.lease else { return }
let result = await loadAccountFeature()
guard session.isCurrent(lease) else { return }
apply(result)
```

Sign-out closes scope immediately, cancels its predecessor and awaits completion
before clearing. That preserves ordering even when a vault ignores cancellation.
Revoke follows successful clear and may leave a warning in signed-out state.
Local-clear failure stays blocked/incomplete. Shutdown permanently closes the
owner without deleting its credential; a delayed UI Task cannot revive it.

## Evidence and limits

All thirteen Swift package checks pass: shared admission/versioned credentials,
restore/rotation/new-owner leases, expiry/account changes, invalid-command
admission, transient versus rejected renewal, issuer/scope mismatch, load/save/
clear failure, expired grants/revoke warnings, late saves, external cancellation,
demo rotation and late defects after shutdown/cancellation. Controlled continuations supply
race evidence without guessing native timing. Consumer/native results are recorded
in the app handoff. Package tests use memory/fakes, not macOS personal Keychain.

One controller owns the namespace and composition shares the device vault.
The foundation itself supplies no real authority, background
renewal, device-lock/biometric or physical hardware claim is supplied.
Read [Keychain substrate](../../../substrate/keychain-credentials-and-accessibility.md),
[observable owner mechanics](../../../language/swift-observable-stores-and-service-protocols.md)
and [scope lifetime](../../../../../../notes/patterns/session-scope-and-credential-lifetime.md).
Next: how should a real authority expose rotating refresh token uncertainty,
and which feature work should be cancelled when its lease becomes stale?

## Authenticated HTTP

The account-access batch, 2026-10-10, adds
[AuthenticatedHTTPClient](../../../../packages/FoundrySession/Sources/FoundrySession/AuthenticatedHTTPClient.swift)
and the manifest's local HTTP dependency. The existing HTTP implementation does
not depend on Session. Its async port permits a MainActor-owned adapter with
one captured lease and a trusted transport/base configuration.

## Go authority and account directory — 2026-10-13

Read [GoSessionAuthority](../../../../packages/FoundrySession/Sources/FoundrySession/GoSessionAuthority.swift),
[wire admission](../../../../packages/FoundrySession/Sources/FoundrySession/GoSessionWire.swift)
and [HTTPAccountDirectory](../../../../packages/FoundrySession/Sources/FoundrySession/HTTPAccountDirectory.swift).
The factory admits a canonical API base and derives issuer identity with CryptoKit
SHA-256. The Go protocol label is checked independently of that deployment identity.
Local DateFormatter instances use POSIX locale, Gregorian calendar and UTC; fraction
admission retains microsecond ordering and floors access expiry to milliseconds.

The authority composes credential issuance and Account bootstrap/read through its
private HTTP client; an active lease cannot exist yet. Renewal admits a known
rotation, invokes the controller-owned checkpoint and then reads the saved account.
The Sendable async callback hops to the MainActor controller for vault ownership;
the authority never receives a vault or publishes a partial session. Default
protocol implementation keeps existing one-step authorities compatible.

Conceptual example: save rotation, fail Account with 503, remain blocked, then
retry using the saved rotation. The controller distinguishes known/confirmed
credentials, flushes pending saves before another renewal and skips duplicate
confirmed saves during activation. A lost grant response remains uncertain.

[Five checks](../../../../packages/FoundrySession/Tests/FoundrySessionTests/GoSessionTests.swift)
consume shared fixtures and exercise recovery, scope refusal and discovery privacy.
[Real Go acceptance](../../../../packages/FoundrySession/Tests/FoundrySessionTests/GoNativeSessionTests.swift)
uses URLSession, memory vault and an owned Go API for scope/renewal/reuse/sign-out.
`make go-native-test` supplies that endpoint; the optional test is skipped in the
ordinary suite rather than pretending a fake is server execution. Native vault/UI
evidence is recorded by the app separately.

Related: [checkpoint pattern](../../../../../../notes/patterns/deployment-authorities-and-rotation-checkpoints.md)
and [integration behavior](../../../../../../contracts/behavior/go-native-session.md).
Next: supply remote account data behind its own port and verify extracted products.

Construction requires HTTPS unless loopback HTTP is explicitly enabled. Request
admission retrieves only the captured lease's token, rejects caller Authorization
and invalid Bearer syntax, then copies all request options while owning that
header. After the one underlying attempt, cancellation and the same lease gate
both returned values and thrown errors. A stale non-cancellation defect becomes
the scope-changed value; current unexpected errors still propagate. A 401 leaves
the controller and vault unchanged and is never replayed.

Example: admit a request as Alex, close the lease before its response returns,
then reject the old response instead of populating Sam's feature. No gate can
prevent server effects from an already accepted request.

[Authenticated tests](../../../../packages/FoundrySession/Tests/FoundrySessionTests/AuthenticatedHTTPTests.swift)
cover shared Bearer fixtures, option/header ownership, default HTTPS/relative paths,
401/no mutation, renewal/expiry/sign-out, late success/failure/defect and native
cancellation. [URLSession check](../../../../packages/FoundrySession/Tests/FoundrySessionTests/AuthenticatedNativeTransportTests.swift)
observes the header and one 401 through a URLProtocol fixture. All twenty package
cases pass, including the original thirteen lifecycle cases. No real TLS/provider
exchange or automatic refresh policy follows from this evidence.

Read [native transport/digest mechanics](../../../substrate/cryptokit-partitions-and-authenticated-urlsession.md),
[shared ownership](../../../../../../notes/patterns/account-partitions-and-leased-work.md)
and [behavior](../../../../../../contracts/behavior/account-access.md).
Next: what issuer/audience/replay policy should a connected authority own?

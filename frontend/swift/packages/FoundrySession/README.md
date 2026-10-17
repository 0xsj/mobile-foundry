# Session foundation

AccountManagement now supplies versioned owner account reads/create/rename behind
a replaceable port. The Go adapter uses an injected exact-lease client and strong
If-Match; it owns no renewal, selection or replay. See [workspace behavior](../../../../contracts/behavior/native-workspaces.md).

[Native registration](../../../../contracts/behavior/native-registration.md) adds an independent IdentityRegistration port and one-attempt HTTP adapter. Its result confirms a profile; session issuance and personal-account bootstrap remain explicit sign-in work. Generated Daily selects transient forms and clears password drafts.

Identity/account values, admitted sign-in and credential records, an injectable
SessionAuthority/CredentialVault, Keychain and memory vaults, and a main-executor
SessionController. DemoSessionAuthority is an explicit local fixture.

GoSessionAuthority now composes real Identity/Account HTTP behind that port.
HTTPAccountDirectory accepts a lease-bound HTTP client. Renewal checkpoints
rotations before account lookup, and explicit selection persists a new scope.
Read [Go integration](../../../../contracts/behavior/go-native-session.md).
`make go-native-test` owns a Go API for actual URLSession/OkHttp acceptance.

Activate only after credential persistence. Renew validates the saved issuer
and scope. Expiry closes lease access; sign-out supersedes pending work, waits
for its completion and then clears credentials. Shutdown closes an owner while
keeping the saved credential for a fresh owner to restore.

Run `make session-test` from the repository root. Native Keychain checks run in
`make ios-test`, using isolated test service names. See [behavior](../../../../contracts/behavior/session.md),
[construction](../../../../docs/blueprints/session.md) and
[walkthrough](../../notes/modules/packages/FoundrySession/README.md).


## Recovery and foreground live hints

IdentityRecovery separates public reset/code operations from authenticated
verification/contact/password changes. HTTPIdentityRecovery accepts an exact-lease
client for authenticated work. AccountLiveHints carries only scoped refetch hints;
its Go implementation binds the canonical deployment, lease and account. Native
URLSession/OkHttp transport remains replaceable. Product owners implement forms,
conservative password uncertainty, foreground reconnect and queued reads.
Read [behavior](../../../../contracts/behavior/native-identity-live.md) and [setup/evidence](../../../../docs/blueprints/native-identity-live.md).

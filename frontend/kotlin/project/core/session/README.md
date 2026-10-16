# Session foundation

Identity/account values, admitted sign-in and credential records, an injectable
SessionAuthority/CredentialVault, Keystore and memory vaults, and a single-UI-executor
SessionController. DemoSessionAuthority is an explicit local fixture.

GoSessionAuthority now composes real Identity/Account HTTP behind that port.
HTTPAccountDirectory accepts a lease-bound HTTP client. Renewal checkpoints
rotations before account lookup, and explicit selection persists a new scope.
Read [Go integration](../../../../../contracts/behavior/go-native-session.md).
`make go-native-test` owns a Go API for actual URLSession/OkHttp acceptance.

Activate only after credential persistence. Renew validates the saved issuer
and scope. Expiry closes lease access; sign-out supersedes pending work, waits
for its completion and then clears credentials. Shutdown closes an owner while
keeping the saved credential for a fresh owner to restore.

Run `make session-test` from the repository root. Actual Android Keystore and
gallery checks run in `make session-ui-test` on a device/emulator. See
[behavior](../../../../../contracts/behavior/session.md),
[construction](../../../../../docs/blueprints/session.md) and
[walkthrough](../../../notes/modules/project/core/session/README.md).

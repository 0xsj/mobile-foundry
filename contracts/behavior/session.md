# Session foundation

Status: implemented, 2026-10-10. The catalog authority is a local
fixture, not authentication. [Go integration](go-native-session.md) now supplies a
real authority separately; Supabase/Firebase adapters remain future work.

## Values and ports

Session owns Principal (person ID/display name), Account (data ownership ID/name),
SessionScope (authority/person/account IDs), admitted SignIn login/password,
SessionGrant (scope, access/refresh tokens and absolute access expiry), and
StoredCredential (version one, scope and refresh token). Secrets never belong to
the public SessionSnapshot. Passwords and access tokens are not persisted.
Nonempty bounded strings and finite positive expiry are admitted before use;
unknown credential versions/malformed bytes are typed refusals, never empty data.

SessionAuthority exposes signIn, renew and revoke, including an owned credential
checkpoint for multi-step renewal. Optional AccountSelectingAuthority supplies
guarded switching. Its ID identifies the issuer;
reopening a saved session requires authority renewal, not trust in local account
metadata. Renew must preserve the saved authority/person/account scope. Expected
failures are kernel values; cancellation and unexpected defects propagate.
CredentialVault exposes load/save/clear and serializes operations on its instance.
The session controller is the sole writer to its chosen vault namespace.

## Lifecycle and admission

Restore loads the refresh credential, validates issuer, renews through the
authority, admits the grant and saves its rotated credential before publishing
an active scope. Missing credentials produce signed-out state. Transient failures
retain saved credentials but publish no active scope; explicit retry is possible.
Unauthenticated renewal clears saved credentials. Scope mismatch is refused and
cleared. A vault clear failure is reported instead of claiming successful cleanup.

Sign-in validates before side effects, clears the previous saved credential,
then authenticates and saves before activation. This prevents a failed account
replacement from silently keeping the earlier person's saved session. Renew
closes the previous active scope before asynchronous work. Expired grants are
never admitted; a lease check also rejects access after its expiry time even
before a UI phase changes. There is no automatic retry, timer or HTTP 401 replay.

One operation is admitted at a time. Sign-out can supersede an in-flight operation:
close the scope and invalidate its generation immediately, cancel it, await its
completion, then clear the vault. This ordering prevents an abandoned save from
resurrecting credentials after clearing. Revoke follows local clearing; a remote
revocation failure is shown while local state remains signed out. Local-clear
failure leaves access blocked and sign-out incomplete; callers must explicitly
retry. A later process may still restore credentials that could not be cleared.

Multi-step Go renewal checkpoints a known rotation before account lookup. The
controller distinguishes known in-memory credentials from confirmed vault saves;
after a failed checkpoint it retries persistence before another renewal. Restore
in that owner retains the pending rotation rather than reloading an older consumed
token. Process exit or a missing rotation response remains uncertain.

Public scope leases contain identity, an owner discriminator and an owner-local epoch. Account changes,
renewal, sign-out and shutdown invalidate them. Late results and diagnostics
cannot publish after invalidation. Shutdown permanently closes that owner, cancels observation/work but retains
saved credentials; it is not sign-out. It does not promise to interrupt a native
operation or undo an authority request already accepted.

## Native storage and catalog scope

iOS uses a non-synchronizable generic-password Keychain item with
AfterFirstUnlockThisDeviceOnly accessibility. Android uses AES-GCM with a key in
AndroidKeyStore and atomic encrypted bytes under noBackupFilesDir. Missing keys,
authentication failures/corrupt ciphertext and native IO failures are typed values;
no plaintext fallback or automatic reset is supplied. Native operations run off
UI execution. Hardware backing and biometric gating are not required or claimed.

Open Studio → Open catalog → Identity and session. Select Device or
Memory; demo accounts are Alex and Sam, password catalog-demo. Renew, simulate
expiry, reject renewal in the current fixture authority, restore and sign out. Fixture
revocations live only in the authority instance; the demo is not server security.
Leaving the gallery shuts down its controller; device restoration revalidates
the saved refresh token with a new demo authority. Memory lasts only in that owner.
Existing Local notes remain a separate guest collection and are not relabeled,
partitioned or authorized by demo sign-in. Account-scoped Notes require a later
data migration/retention contract. The app's Account tab remains a placeholder.

## Verification scope

Shared fixtures specify command/credential/grant admission. Controller checks
exercise restoration, rotation, expiry, scope mismatch, storage failures,
revocation, and superseded work. Native vault checks use real Keychain/Keystore
with isolated names, including replacement, clearing and malformed data. Catalog
interaction and execution counts are recorded in learning notes. Real identity,
OAuth/passkeys, provider SDKs, cross-controller storage writes, biometric/device
lock experiments remain separate. Subsequent account-access and Go integration
contracts supply authenticated HTTP and real server-owned account admission.

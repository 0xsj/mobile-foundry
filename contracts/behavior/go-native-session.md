# Native Go session and account integration

Swift/Kotlin GoSessionAuthority implements SessionAuthority; HTTPAccountDirectory
implements AccountDirectory. They compose [Identity HTTP](../http/identity-sessions-v1.md),
[Account HTTP](../http/account-audit-v1.md) and [native session lifecycle](session.md).
Account selection stays on each device, while the backend owns admission.

## Deployment and transport

The factory takes an API base, transport, initial account selection, per-request
timeout and explicit loopback-HTTP option. HTTPS is the default; local HTTP is
admitted only for `localhost`, `127.0.0.1` and `::1`. The selected native transports
disable redirects, cookies, credential stores, retries and 401 replay. Callers can
cancel; each request has its own budget rather than one whole-operation timer.

Reject base credentials/query/fragment/traversal. This adapter admits ASCII
hostnames/IPv4 or loopback IPv6 and simple unescaped ASCII prefix segments.
Canonicalize scheme/host case, omit default ports/trailing slashes, preserve prefix
case. The issuer ID is `go.identity.v1.` plus lowercase SHA-256 of UTF-8
`go.identity.v1\n<canonical-base>`. The wire label alone cannot distinguish
deployments. Saved issuer mismatch refuses before IO. The catalog also uses
separate device-vault namespaces per issuer, distinct from the demo authority.

## Account-bound grants

Sign-in admits the Go grant, then bootstraps the personal account or reads a
configured existing account. Validate owner, canonical IDs/names, JSON-safe
versions, UTC timestamps, token purpose/shape and expiry. Direct account responses
must agree with their ETag and requested ID. Never substitute a principal ID for
an account or publish access after a failed account lookup.

A known sign-in grant followed by an expected account failure triggers one bounded
best-effort family revocation; the primary failure is retained. Cancellation,
unreadable/lost responses or cleanup failure do not establish remote rollback.

AccountSelectingAuthority reads the chosen account with current access and returns
the same issuer/principal/credentials/expiry with a new account scope. The controller
closes the old lease before lookup, persists the new scope before activation and
refuses changed principal/requested IDs. Failure blocks access with the previous
credential retained for explicit restore. Accepted writes keep their original target.

AccountDirectory reads a bounded owner page through an injected lease-bound HTTP
client. It forwards opaque cursors and checks owner/order/duplicates/page shape.
The directory owns no credentials. The catalog clears pending work, rows and
cursors on lease changes. This is discovery, not membership or remote Notes CRUD.

## Rotation checkpoint

Go rotates before account lookup. The controller supplies its owned vault callback
to the checkpoint overload of `renew`. After admitting rotation and unchanged
principal, the adapter checkpoints a credential with the **saved account scope**,
then reads that exact account. Renewal/restoration never bootstraps a replacement.

Checkpoint success precedes further IO. A transient account failure therefore
leaves the current rotation saved, with no active lease; explicit retry uses that
family. Activation skips an already confirmed save. Unauthenticated/forbidden
renewal clears credentials; other failures retain them without changing scope.

The controller distinguishes known credentials from confirmed persistence. A
failed checkpoint stops account IO and retains the known rotation in memory.
Retry/restore in that owner retries persistence before consuming another refresh;
it does not reload an older consumed token. Process exit before successful saving,
or a lost/unreadable rotation response, can still lose the current credential.
The checkpoint does not solve that uncertainty or imply blind renewal retries.
Direct authority callers own persistence; features should use the controller/
checkpoint path. Legacy one-step authorities use the default implementation.

Sign-out keeps the cancellation/join/clear barrier. Go accepts a known consumed
family token for revocation when rotation was superseded. HTTP 401 remains one
refused request and does not automatically renew, clear storage or mutate session.

## Catalog and acceptance

Open **Go identity and accounts** in the native catalog. Configure the `/v1` base,
Device/Memory and loopback option; Connect, then sign in with a registered Go user.
List/select workspaces, renew, reopen and sign out. Password drafts are ephemeral;
snapshots/diagnostics contain no credentials. The demo gallery remains separate.

`make session-test` covers shared fixtures and controlled rotation/account/storage
failures. `make go-native-test` owns a temporary Go memory API and uses real
URLSession/OkHttp for sign-in, listing, switching, renewal/restore, isolation,
refresh reuse and sign-out. `make go-native-device-test` adds actual catalog/native
vault checks on an iOS simulator and connected Android device. It owns only its
API, fixtures and temporary reverse-port mapping.

Remote Notes, synchronization/receipts, production Identity workflows, backend CLI
extraction and independently generated connected-product acceptance remain separate.

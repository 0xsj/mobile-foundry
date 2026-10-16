# Deployment authorities and rotation checkpoints

A rotating authority needs both a deployment-bound scope identity and a durable
credential checkpoint before subsequent account IO can fail independently.

Origin: native Go integration, 2026-10-13. Compared existing native session/vault
ports with Go Identity's one-use refresh rotation and Account's separate scope.
Source and controlled tests establish checkpoint ordering; real URLSession/OkHttp
tests establish the backend composition. Device observations belong in app notes.

## What and why

The wire label `go.identity.v1` is a protocol name shared by every deployment.
Using it as the native issuer would let a stored credential be sent to a different
configured API. Both adapters hash the same admitted canonical base into issuer
identity and the catalog uses that identity in a separate vault namespace.
Host case/default port/trailing slash aliases agree; different ports/prefixes
remain distinct. This is endpoint naming, not certificate pinning or proof that
two DNS aliases refer to the same authority.

Go admits a principal/session without choosing an Account. Sign-in explicitly
bootstraps or reads an account, while restoration reads the **saved account**.
Substituting a personal default during renewal would silently retarget data and
violate the existing scope/accepted-write rules. The native service and query
ports stay interchangeable with future provider implementations.

## Rotation changes failure recovery

Conceptual example: renew → Go commits new refresh token → account lookup fails.
If the client returns only a generic failed grant and retains the old token,
the next retry replays a consumed credential and Go revokes its family. The
account's temporary failure has become credential loss.

The controller now supplies a checkpoint callback. After strict grant/principal
admission, the Go adapter checkpoints the new token with the unchanged scope,
then reads the account. A failed lookup leaves the rotation saved and access
blocked; an explicit retry uses the current credential. No active access is
published until the full grant is admitted and persistence is confirmed.

Known rotation and confirmed persistence are separate controller values. If saving
fails, account IO stops and the owner retains the current credential in memory.
Retry first retries saving; restore in that owner does not reload an older token.
A fresh process cannot recover a rotation that was never saved. Nor does this
callback recover an unreadable/lost renewal response. These remain write
uncertainty, with no automatic renewal/replay guarantee.

The callback also remains behind generation/cancellation checks. Sign-out joins
abandoned work before clearing; an uncancelable late save cannot resurrect a
signed-out credential. Go's known-family sign-out accepts consumed historical
credentials, so it can revoke a family when a concurrent rotation was superseded.

## Selection and discovery

Selection is a guarded read with current access, followed by a newly persisted
scope. It closes the previous lease, retains principal/credentials/expiry and
never changes a principal-global backend preference. AccountDirectory receives
an already lease-bound HTTP client, owns only page projection, and exposes no
credentials. Feature owners clear rows/cursors/tasks when the lease changes.

The HTTP boundary still makes one attempt. A 401 is a refusal, not a signal to
silently renew, replay a write or mutate the session. Real two-principal tests
exercise backend denial independently of client-side lease cleanup.

## Evidence, gotchas and actual use

[Shared configuration](../../contracts/fixtures/go-session/configuration.json)
and [response fixtures](../../contracts/fixtures/go-session/responses.json) cover
canonical endpoint identity, token purpose/padding, UTC precision, IDs, owner and
version admission. Five new controlled checks per platform cover checkpoint-before-
lookup, transient account failure, pending-save recovery, issuer/principal mismatch,
selection restoration and malformed discovery pages.

`make go-native-test` owns a memory Go API. The real native transports observe
sign-in/bootstrap, two account pages, selection, renewal/restoration, other-person
denial, consumed-token reuse, explicit clearing and sign-out revocation. These
host checks use memory vaults; they do not establish device persistence by themselves.
Catalog/device acceptance adds real native vaults separately. See
[behavior](../../contracts/behavior/go-native-session.md) for the current boundary.

Related: [session lifetime](session-scope-and-credential-lifetime.md),
[account partitions](account-partitions-and-leased-work.md),
[Go principal/account ownership](../../backend/go/notes/patterns/principal-session-and-account-boundaries.md).
Next: remote Notes receipts/reconciliation before registering a connected CLI profile.

# Private providers behind the push port

Claim: a provider adapter can confirm only the narrow result its protocol proves;
uncertain responses must preserve both the registered address and the possibility
that a send already happened.

Origin/evidence: 2026-10-18 Rust 1.86, Reqwest 0.12.28, Ring 0.17.14 and local
TLS/HTTP2 acceptance against 32 shared response and 21 HTTP cases. The
[Go provider walkthrough](../../../../../go/notes/modules/internal/notifications/providers.md)
and [shared contract](../../../../../../contracts/behavior/push-providers.md)
are the reference. Read [Rust scope/limits](../../../../../../contracts/behavior/rust-push-providers.md), then:

1. [Composition facade](../../../../internal/notifications/src/lib.rs): private
   configuration loads one opaque owner; application callers receive only
   Arc&lt;dyn PushGateway&gt;. Drop closes pools after process requests/workers drain.
2. [Gateway/transport](../../../../internal/notifications/src/infra/push/mod.rs):
   bounded local files, exact routes, fixed HTTPS, disabled redirects/proxies/retries,
   bounded replies, safe errors and cooperative cancellation.
3. [APNs](../../../../internal/notifications/src/infra/push/apns.rs): PKCS8 P-256,
   raw 64-byte ES256 signatures, fifty-minute cache and binding-relative rejection.
4. [FCM](../../../../internal/notifications/src/infra/push/fcm.rs): PKCS8 RSA,
   signed OAuth exchange, coalesced cache and typed invalid-address evidence.
5. [Manual HTTP edge](../../../../internal/notifications/src/transport/http/v1/installations/push.rs):
   existing credential/proof guard, required revision, current ownership and
   projection of the named dispatch command. No credentials enter domain input.
6. [Process wiring](../../../../cmd/server/src/main.rs) and
   [configuration](../../../../pkg/config/src/lib.rs): owned Identity is mandatory;
   no sender is selected by default, and startup never sends.

Example: an installation rotates at time T. Apple's Unregistered response refers
to T minus one second. Even though the device address is rejected somewhere in
the provider history, that timestamp does not reject the current binding. The
adapter returns an error, leaving the token intact. A current typed rejection
can return InvalidTarget; [dispatch](push.md) then retires only the original
owner/revision. These are two independent guards against stale cleanup.

Rust's enums admit only Accepted/InvalidTarget from providers. Ring's ES256 fixed
signing form already produces the JWT-required raw R/S representation; ASN.1 DER
ECDSA would be a different encoding. FCM's RS256 assertion is not itself the send
credential: it first obtains an OAuth token. Tokio's async Mutex coalesces that
refresh without holding a synchronous mutex across network I/O. APNs signing is
local and short, so its cache uses a synchronous mutex without an await.

The transport waits on borrowed futures and periodically checks the synchronous
Scope port. Canceling a cache waiter drops only that wait; canceling an OAuth
exchange drops the guard, allowing a later explicit caller to refresh. Canceling
a send can never prove that the provider did nothing. No retry follows automatically.
See [the transport substrate note](../../../substrate/reqwest-provider-boundaries.md).

Verification: [local protocol tests](../../../../internal/notifications/src/infra/push/tests/protocol.rs)
verify both JWT signatures, payload/headers, each shared response, concurrent FCM
refresh, wall-clock boundaries, TLS trust/hostname, redirects, reply limits and
cooperative cancellation. Invalid OAuth responses never reach the message endpoint;
retained gateway handles refuse work after facade closure.
[Configuration tests](../../../../internal/notifications/src/infra/push/tests/configuration.rs)
check relative credentials, exact routes, size limits, private diagnostics and
fixed endpoints without network. [Composed HTTP checks](../../../../cmd/server/tests/support/push_http.rs)
use real Identity credentials and proof/CAS registration with an injected gateway,
verify repeated IDs are new sends, and prove uncertainty versus guarded retirement.
The same checks run with separate PostgreSQL owners. The
[runner](../../../../../../scripts/push_providers_parity.py) verifies compiled
default-off/explicit-selection startup and clean shutdown in both languages.

Packaging evidence: the [Go inventory](../../../../../../templates/backends/go.json)
excludes both repository-fixture tests; [CLI checks](../../../../../../cli/tests/test_backends.py)
verify those files are absent. A fresh CLI-generated Go project at
`/private/tmp/mobile-provider-extraction-d_8mw7by/provider-check` passed independent
format/architecture/vet/build, full race tests and owned memory API smoke. The
source module was rewritten to `example.com/provider-check/backend`, with no
dependency on this repository's provider corpus. This protects existing Go
extraction; it does not register a Rust profile.

Gotchas: no live Apple/Google account, device-issued token or actual delivery was
tested. Private JSON/email/RSA admission is a documented supported subset, not
complete Go decoder equivalence. Zeroizing owned credential buffers does not
prove erasure of copies held by libraries/allocators. HTTP can project cleanup
failure but cannot return the application's rich partial outcome as a receipt.
Native/CLI Rust selection remains unregistered; [retained jobs](jobs.md) have separate acceptance.
Related: [known rejection](../../../patterns/known-rejection-and-unconfirmed-cleanup.md),
[installation ownership](installations.md). Next: read [retained job ownership](jobs.md)
for uncertain sends and explicit replacement after restart.

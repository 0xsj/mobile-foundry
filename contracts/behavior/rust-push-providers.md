# Rust optional push providers and manual preview

Established 2026-10-18. Rust selects the bounded manual-preview subset of
[provider behavior](push-providers.md) through Notifications' existing
[dispatch foundation](rust-push-dispatch.md). Go remains the reference. The
default runtime has no sender or preview route. Durable jobs remain unselected.

`PUSH_CONFIG_FILE` explicitly selects a private JSON file with 1–16 unique exact
provider/application/environment routes. Selection requires `AUTH_MODE=identity`.
Empty means off. Configuration is at most 64 KiB; APNs PKCS8 P-256 PEM at most
16 KiB; FCM service-account JSON at most 32 KiB. Credential paths resolve relative
to the configuration file, with absolute paths allowed. Loading reads local files
and constructs clients without contacting providers. Unknown fields, unsupported
routes, mismatched project/universe/token URI and inappropriate key types fail
startup with sanitized errors. No key or configuration file enters source copies.

APNs uses fixed Apple sandbox/production endpoints, validated HTTP/2 TLS, ES256
provider JWTs, the exact application topic and canonical lowercase hexadecimal
device addresses. The JWT cache lasts less than fifty minutes and refreshes on
backward clock movement. The payload is the shared fixed preview alert plus
version/kind/preview ID; callers cannot supply arbitrary notification content.
Only an empty HTTP 200 acknowledgement means Accepted. BadDeviceToken on 400
means InvalidTarget. Unregistered on 410 requires a positive millisecond timestamp
at or after the original binding update and no later than current time plus five
minutes. Old/missing/overflow/future timestamps cannot retire a newer binding.

FCM uses the fixed Google OAuth and selected-project v1 send endpoints. A private
RS256 assertion exchanges for a bounded printable access token. Concurrent callers
share one refresh through a cancellable gate; the cache refreshes within sixty
seconds of expiry and on backward wall time. The send payload pins the registered
token/application, zero TTL and shared preview content. Accepted requires the
selected project's nonempty message-name acknowledgement. InvalidTarget requires
HTTP 404, envelope code 404/status NOT_FOUND and typed FcmError UNREGISTERED evidence.
Generic 404, invalid arguments, auth and quota errors preserve the installation.

The HTTPS transport refuses redirects and ambient proxies and explicitly disables
Reqwest's protocol retries. Replies are bounded to 16 KiB; client request/connect
limits are five seconds, inside the command's shared five-second attempt budget.
There is one message POST, with a separate OAuth exchange if necessary. Scope
polling cancels stalled I/O/cache waits without spawning application tasks.
Transport causes, URLs, response bodies, JWTs and tokens never enter failures/logs.
The facade owns the client pools; it closes them after HTTP/worker drain and before
database pools. Reqwest/Hyper own their internal connection drivers; the application
does not claim to hold/join those library task handles. Retained gateway handles
refuse new calls after facade closure.

Only selection mounts `POST /v1/installations/{id}/push-preview`. Other methods
return 405/Allow POST before authentication. The request requires current Identity
credentials, one X-Installation-Key, strong If-Match and bounded strict JSON
containing canonical preview_id. It refuses queries, Idempotency-Key and
If-None-Match. Ordered string duplicates, ASCII field casing and null no-op match
Go admission. Possession alone cannot send under a different principal/session;
the edge proves current binding and revision before dispatch rechecks eligibility.

The 200 body contains exactly preview_id, disposition and retired, with no ETag
and no-store caching. Provider/retirement failures use ordinary public problems.
The application retains known rejection with nested cleanup Result even when
HTTP can only project the failure. A repeated ID sends again: this endpoint has
no receipt, automatic retry, queue, fanout or exactly-once guarantee. Provider
acceptance proves neither device delivery nor display/open. Check/send races remain.

Evidence: `make backend-push-providers-parity-test` executes 32 shared provider
response and 21 HTTP cases independently in Go/Rust, local verified TLS/HTTP2,
JWT signature/payload checks, cache refresh/coalescing, cancellation, bounds and
redirect refusal. Compiled APIs prove default absence and explicit selection
without any cloud request. `make backend-postgres-parity-test` additionally runs
the same HTTP guards and outcomes with separate Identity/Notifications databases.
Existing dispatch rollback/race/cleanup tests continue unchanged; no migration changes.
The existing Go CLI excludes the repository-only fixture tests; a fresh generated
Go backend passes check, full race tests and memory process smoke independently.

Limits: private configuration uses exact lowercase fields and rejects duplicate
members; it does not emulate all Go JSON case/null/duplicate behavior. Provider
response DTOs also refuse ambiguous duplicate typed fields. FCM email configuration
admits a conservative bare ASCII subset. Ring 0.17.14 admits RSA private moduli
2048–4096 bits with its supported key constraints; Go accepts a wider key subset.
Live cloud credentials, signing/provisioning, device delivery, Rust native/CLI
selection and deployment acceptance remain separate. Rust remains a foundation
candidate. Read [inventory](../../backend/rust/PARITY.md) and
[walkthrough](../../backend/rust/notes/modules/internal/notifications/providers.md).

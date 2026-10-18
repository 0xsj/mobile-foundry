# Rust recoverable live Notes hints

Established 2026-10-18. Rust implements the native-bearer portion of
[Realtime](realtime.md) through a reusable bounded hub, consumer-owned application
ports and a versioned WebSocket edge. Notes remains a replaceable example domain.

GET `/v1/accounts/{account}/realtime` accepts native Authorization bearer headers.
Reject Origin/Cookie headers, even empty, nonempty query strings, invalid account
IDs, missing credentials and denied account access before upgrading. Subprotocols
do not supply authentication. HEAD is 400; unsupported methods are 405 with
`Allow: GET, HEAD`. A bare `?` is allowed, matching Go's RawQuery rule here.
There are no query tickets, browser cookies, compression or selected subprotocol.

Realtime consumes `account.access.v1` as caller `realtime`, through its own
AccountAccess port; missing/invalid/dependency answers remain failures, distinct
from explicit refusal. Feed/Subscription are separate ports. Neither Realtime
nor Notes imports another module's state, SQL or business implementation.

The process selects one hub shared by Notes' Changes adapter and Realtime's
local Feed. It admits 256 subscriptions total and eight per principal. Each
subscription retains at most one dirty signal; repeated publications coalesce,
never block a writer and carry no content. Drop/release and close are idempotent.
Closing the hub denies new watches and ends existing watches after any buffered
signal. Empty keys/owners and zero/exhausted capacity refuse admission.

Subscribe before sending `ready`. Both `ready` and `changed` contain exactly
`type`, `resource: "notes"`, `account_id`, `refetch: true`. The client refetches
HTTP state after ready/reconnect/foreground and hints. Notes publishes only after
a confirmed, validated store result, including exact receipt replay. Failed
writes publish nothing. A commit with lost acknowledgement can produce no hint;
this is deliberately outside durable event delivery. No message contains titles,
record data, credentials or write authority.

The upgraded connection owns a separate ten-minute lifetime, independent of the
ordinary fifteen-second HTTP scope. Recheck the same bearer, principal, session
and original account before each hint and every five seconds, with one two-second
budget for the complete authorization query. Refusal, dependency failure and
timeout close the stream. Writes/upgrade/Pong waiting have three-second budgets.
Matching Pong bytes discharge the outstanding Ping; a new tick cannot extend
an unanswered Ping. Limit incoming frames/messages to 1024 bytes; application
data, malformed input and disconnect end the subscription.

The Realtime owner retains upgraded tasks in a JoinSet. Shutdown stops admission,
closes sockets and joins those tasks before workers/Identity/provider/filesystem
resources and database pools close, including listener-error paths. A canceled
close future retains the handles for a subsequent close. Unwinding task defects
fail owner readiness and remain private; abort-mode panics remain outside this
recovery boundary. No raw protocol error, bearer, URI or panic payload is logged.

`make backend-realtime-parity-test` runs eight common hub scenarios (38 operations),
15 shared HTTP admission cases, Rust application/protocol/lifecycle checks and
compiled Go/Rust memory flows. Connections exceed 15 seconds with real Ping/Pong;
Notes CRUD/replay, failed-CAS silence, scope isolation, eight-watch capacity,
renewal/sign-out closure and SIGTERM with an open socket are exercised.
`make backend-postgres-parity-test` additionally executes the same confirmed-write
and credential lifecycle flow against each API using an owned disposable database.

Limits: process-local lossy hints, one API replica, no durable replay/cursor or
cross-process broker. Handshake failure bodies/status variants beyond the corpus
are library-owned differences (for example unsupported-version handling). Go's
library writes 101/handshake failures directly; Rust also applies its boundary
headers. Full parser/header/routing equivalence is not claimed. TLS ingress,
proxy/metrics/deployment and independently generated Rust/native products remain
separate acceptance; backend wire checks alone do not register a CLI profile.

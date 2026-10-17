# Account-scoped Realtime hints

Realtime is an optional transport capability selected by the current full Go
backend. HTTP remains authoritative. The initial product resource is Notes.

`GET /v1/accounts/{account}/realtime` upgrades a native WebSocket connection with
one Authorization: Bearer header. Normal principal resolution and the consumer-owned
AccountAccess port must succeed before subscribing. Query parameters, cookies,
browser Origin headers and non-GET requests are refused. No bearer is placed in a
URL or subprotocol. Browser ticket issuance is a separate contract.

A successful connection first receives `{"type":"ready","resource":"notes",
"account_id":"…","refetch":true}`. Confirmed Notes store results publish a
coalesced `changed` hint with the same fields. Payloads contain no titles, content,
principal data or write authority. Duplicate/replayed commands may repeat hints.
A failed write does not publish. A crash between commit and publish can lose a
hint: this is deliberately separate from required integration facts/outbox delivery.

Subscribe before sending ready. Clients refetch HTTP after ready, every changed
hint, foreground return and reconnect. Hints are not a replay log, data snapshot,
command acknowledgement, presence protocol or offline synchronization system.

The generic broadcast hub defaults to 256 subscriptions, eight per principal and
one coalesced dirty signal per subscription. Queues cannot grow with writes. The
Realtime-owned Feed and AccountAccess ports and Notes-owned Changes port can use
other adapters. Default composition uses one process-local hub and one API replica.
A shared feed adapter is required before load-balanced/multi-process hint delivery
can be claimed. HTTP refetch still resolves missed hints.

The socket owns a ten-minute lifetime after ordinary HTTP admission. It clears the
HTTP write deadline, uses three-second writes/pings, checks access with a two-second
budget before every message and every five seconds, and closes when the same bearer
or original account is refused. Renewal requires reconnect with the new bearer.
Admission/recheck is not a distributed lock with later revocation; in-flight hints
contain no private record data. Incoming application messages are rejected; read
limits, heartbeats, client disconnect and module stop release subscriptions.
Shutdown closes hijacked sockets explicitly before stores close.

Verification uses real WebSocket frames through the HTTP boundary: unauthorized/
foreign account/origin/cookie refusal, post-commit hint, private payload exclusion,
reconnect ready, operation beyond the normal fifteen-second HTTP budget, bearer
revocation and module stop. Generic hub cases verify scope, capacity, coalescing
and idempotent cleanup. Daily go-connected selects [native Swift/Kotlin consumers](native-identity-live.md); other shells/catalog UI remain separate.

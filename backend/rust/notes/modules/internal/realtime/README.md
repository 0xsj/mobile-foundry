# Native bearer sockets with owned lifetime

Claim: a recoverable live transport can be swapped without coupling business
modules, while explicitly retaining every upgraded connection task.

Origin/evidence: 2026-10-18 Rust 1.86, Tokio 1.53.2, Hyper 1.11.1 and
tokio-tungstenite/tungstenite 0.28.0. Read [the behavior and limits](../../../../../../contracts/behavior/rust-realtime.md).

Source reading order:

1. [Application ports](../../../../internal/realtime/src/app/mod.rs) separate
   AccountAccess, Feed and Subscription. The async Subscribe query checks the
   account before allocating a watch. There is no invented Realtime aggregate.
2. [Capability client](../../../../internal/realtime/src/infra/capabilities.rs)
   asks account.access.v1 as realtime and requires an explicit boolean. Denial
   differs from an unavailable provider or malformed answer.
3. [Local Feed](../../../../internal/realtime/src/infra/local.rs) adapts notes.ID
   and principal ownership to [the bounded hub](../../pkg/broadcast/README.md).
   Notes' [Changes adapter](../../../../internal/notes/src/infra/broadcast.rs)
   publishes through its own port after confirmed store success/replay.
4. [WebSocket v1](../../../../internal/realtime/src/transport/websocket/v1.rs)
   admits native bearer/UUID scope, validates a handshake, reserves a subscription,
   then retains the upgrade future in its owned JoinSet. The async move block
   owns the token in a zeroizing string, actor, account and feed; it does not retain
   the expired HTTP scope. See [upgraded task ownership](../../../language/owned-upgrades-and-cancel-safe-join.md).
5. session sends ready only after another current authorization check, selects
   feed/network/tick/Pong deadline, and emits only four-field refetch messages.
   A biased control/deadline branch prevents incoming control traffic from
   indefinitely postponing the periodic check. Matching Pong clears its deadline.
6. [Facade](../../../../internal/realtime/src/lib.rs) verifies the registered
   capability/caller, supplies routes/readiness, and owns stop/close. Module Drop
   signals stop, but normal process composition still explicitly joins.
7. [Main](../../../../cmd/server/src/main.rs) shares one hub, selects both owned
   adapters, fences sockets on signal and joins them after HTTP drain, including
   listener failures. The [server](../../../../internal/server/src/lib.rs) enables
   Hyper upgrades; its ordinary HTTP connection owner no longer owns the handed-off
   stream. Workers/resources/pools close only after Realtime joins.

Example (Conceptual): subscribe, receive ready, refetch Notes. A later write
commits its receipt and publishes dirty. Several writes may collapse into one
changed message; the next HTTP read provides authority. Renewing the bearer
invalidates the original connection, which must reconnect with the new lease.
See [subscribe-before-refetch reasoning](../../../patterns/subscribe-before-refetch.md).

Verification: [application tests](../../../../internal/realtime/tests/application.rs)
cover refused/dependency/capacity separation and strict capability responses.
[Socket tests](../../../../internal/realtime/src/transport/websocket/v1/tests.rs)
use actual loopback Hyper upgrades for control/data limits, access/session changes,
unanswered Pong, lifetime, capacity release, canceled close, panic readiness and
a connection beyond fifteen seconds. One paused-time test proves the complete
reauthorization budget. [The shared runner](../../../../../../scripts/realtime_parity.py)
checks both compiled APIs with memory and, through the disposable database owner,
PostgreSQL Notes stores. Local tests do not prove a deployed TLS proxy or native UI.

Gotchas: no durable history, multi-process fanout or reconnect cursor exists. A
commit with lost acknowledgement can omit a hint. Rust's decorated handshake
responses differ from Go's library responses outside captured cases. Stop drops
read-only auth futures and sockets, never a business mutation. A caught unwind
fails owner readiness without exposing its payload; abort-mode panics still abort.
No Rust CLI/native product is registered by these backend checks.

Next: how should a portable ingress profile bound and authorize upgraded traffic?

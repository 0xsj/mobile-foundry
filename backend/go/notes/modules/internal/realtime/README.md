# Realtime subscription boundary

Claim: authorization and feed selection belong behind consumer-owned application
ports, while the WebSocket edge owns protocol and connection lifetime.

Origin: 2026-10-17 Realtime slice. Evidence: source inspection and
[actual loopback socket checks](../../../../tests/realtime_test.go).

## Read the flow

1. [Subscribe query](../../../../internal/realtime/app/query/subscribe.go) validates
   principal/account IDs, distinguishes denied access from failure, then watches.
2. [Account client](../../../../internal/realtime/infra/capabilities/account.go)
   invokes the existing versioned account-access capability as caller realtime.
3. [Local feed](../../../../internal/realtime/infra/local/feed.go) selects an
   account key and principal quota over the generic hub.
4. [WebSocket handler](../../../../internal/realtime/transport/websocket/v1/handler.go)
   admits native bearer headers, upgrades, rechecks, pings and writes bounded hints.
5. [Module](../../../../internal/realtime/module.go) composes ports and exposes Stop
   so hijacked connections close before application resources.

Conceptual: Notes → app.Changes → local hub → Realtime app.Feed → socket hint →
client HTTP refetch. No domain imports another module or owns a WebSocket handle.

## Why these limits

Browser Origin/Cookie and query credentials are refused; a browser-ticket flow
needs its own authority and admission design. Compression is off and application
messages are unsupported. A ten-minute lifetime, bounded checks/writes, ping and
256 global/eight per-principal subscriptions bound the current resource envelope.
Limits are source defaults, not measured public service capacity.

Admission reserves a wait-group slot before upgrading. Stop first refuses new
connections, closes registered sockets and joins all reserved handlers. A
handshake in progress registers only after rechecking stopping. HTTP's ordinary
request timeout is detached after admission; an explicit socket timeout and Stop
replace it. Tests observe account isolation, ready/changed, no private data,
reconnect, the fifteen-second HTTP boundary, revocation and shutdown.

Related: [hint pattern](../../../patterns/bounded-live-hints.md),
[contract](../../../../../../contracts/behavior/realtime.md).
Next: native consumers and a broker adapter are independent additions; no durable
delivery or synchronization is implied by a successful connection.

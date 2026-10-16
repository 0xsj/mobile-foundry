# Client realtime contracts

Reserved contract home under the [module boundary policy](../behavior/module-boundaries.md).
No WebSocket endpoint, broadcast adapter or native live-update service is
implemented yet.

The first selected protocol should define authenticated connection/subscription
admission, account-scoped changed hints, bounded messages/queues, revocation
rechecks, slow-client behavior, close semantics and reconnect/refetch recovery.
Add portable admission and lifecycle scenarios with the implementation.

Realtime maps committed integration facts into recoverable live signals.
Clients read authoritative state over their existing service ports. A socket
message does not establish durable delivery, a write receipt or complete sync.
Presence, streaming, collaboration and push notification integration need their
own explicitly selected contracts.

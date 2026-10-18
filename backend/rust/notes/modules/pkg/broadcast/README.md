# Bounded dirty subscriptions

Claim: a one-slot subscription queue bounds recoverable notification pressure
without making publication wait on a consumer.

Origin/evidence: 2026-10-18 Rust 1.86, Tokio 1.53.2 and Go's existing broadcast
contract. Read [the common scenarios](../../../../../../contracts/fixtures/backend-realtime/README.md).

Source reading order:

1. [Hub and Watch](../../../../pkg/broadcast/src/lib.rs): one short synchronous
   mutex owns registration, owner/global counts and closed state. watch requires
   an Arc receiver because the returned guard must retain its owner.
2. publish iterates only matching keys and uses try_send into a channel of one.
   A full buffer already means dirty; another publication adds no information.
3. Watch::next reads one unit, while close/Drop remove the registration. The Hub
   owns senders and Watch owns its receiver; no task or reference cycle is needed.
4. [Tests](../../../../pkg/broadcast/src/tests.rs) consume the same ordered corpus
   as Go and verify dropped-guard capacity release and redacted Debug output.

Example (Conceptual): twenty writes happen while a screen is suspended. Its one
pending unit still says only “refetch”; a subsequent page read obtains current
state. Another account key receives nothing. Releasing the guard frees both its
global and principal slot, even if the consumer never reads.

Why: this primitive has no business payload, credentials, timers or network
ownership. Notes and Realtime each adapt it behind their own application ports.
The [Realtime walkthrough](../../internal/realtime/README.md) describes selection.
Do not replace committed integration facts with this ephemeral queue.

Verification: eight shared scenarios/38 operations run in Go race tests and
Rust unit tests. Protocol and real HTTP integration are separate checks; the
hub alone proves neither authorization nor delivery after process loss.
Close can still expose its one buffered signal before EOF. Poisoned publication
loses a hint; registration refuses a poisoned owner. Capacity failures do not
mean the entire service is unhealthy.

Related: [subscribe before refetch](../../../patterns/subscribe-before-refetch.md).
Next: what invalidation scope and capacity policy would a remote Feed need?

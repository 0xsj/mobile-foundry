# Outbox delivery, replay and worker lifetime

State/outbox atomicity and consumer/inbox atomicity are separate from delivery
acknowledgment; replay connects those boundaries safely.

Origin: 2026-10-13 memory implementation, race-enabled tests and compiled-process
smoke. Read [envelopes/bus](../../../../pkg/events/events.go),
[dispatcher/worker](../../../../pkg/events/dispatcher.go),
[tests](../../../../pkg/events/events_test.go), then the
[Account store](../../../../internal/account/infra/memory/store.go).

Generic delivery knows envelopes and consumers, not Account/Audit business
payloads. The memory bus clones mutable data per matching consumer and continues
to other consumers after a failure. An absent consumer is a failed delivery.
Registration freezes before workers start.

The dispatcher selects pending records, gives each delivery a cooperative budget,
then records success or a failed attempt. Its mutex prevents overlapping dispatch
passes. A response lost after consumer commit leaves the message retryable; the
consumer's inbox prevents duplicate state changes. Each consumer has its own
identity even if they all receive the same event.

The worker owns its cancel function and join channel. Startup follows registration;
cleanup follows HTTP drain. Cancelling the process signal directly into the worker
would stop delivery while an active handler can still commit. Worker cancellation
keeps the record pending; a delivery deadline counts toward the five-attempt bound.
This distinction avoids an endlessly timing-out message escaping retry accounting.

`make check test smoke` passes independent-consumer isolation, lost acknowledgment,
duplicate/concurrent Audit replay, retry exhaustion, late deadline success,
cancellation and joining in-flight delivery. Outbox exhaustion fails Account
readiness. Memory stores bound retained records; there is no retention/replay
administration or persistent delivery yet.

The critical limitation is process loss: memory state, outbox and inbox vanish
together. An in-process transport can later sit above PostgreSQL stores, but
that requires transaction/crash acceptance beyond these tests. A broker changes
delivery rather than moving business payloads into this package. Next: test a
crash at every state/outbox, consumer/inbox and acknowledgment boundary.

Related: [workflow behavior](../../../../../../contracts/behavior/account-audit.md),
[Account](../../internal/account/README.md), [Audit](../../internal/audit/README.md)
and [composition pattern](../../../patterns/ports-and-module-composition.md).

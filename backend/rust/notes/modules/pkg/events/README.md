# Committed event delivery mechanics

Claim: reusable delivery mechanics can remain independent of business state
when outbox storage is embedded under the producer's existing commit lock.

Origin/evidence: 2026-10-18 [implementation](../../../../pkg/events/src/lib.rs),
[delivery tests](../../../../pkg/events/tests/delivery.rs), canceled-close unit
test and actual Go/Rust Audit process acceptance. Read
[the implemented contract](../../../../../../contracts/behavior/rust-audit.md).

Source reading order: Envelope/validation, subscription Builder and immutable
MemoryBus; [embedded memory records](../../../../pkg/events/src/memory.rs);
[Outbox/Dispatcher/Worker](../../../../pkg/events/src/dispatcher.rs); then producer
adapters in [Account](../../internal/account/README.md) and
[Identity](../../internal/identity/README.md). The Outbox trait is mechanical;
its implementation remains with the producer and never has a foreign store.

Memory records deliberately have no second mutex. Producers validate/map first,
check identity/capacity/scope under their owner lock and append the event with
state. append_admitted is the infallible final insertion, not a replacement for
those checks. An independently locked outbox would allow partial commits.
The retained local fact ID becomes the integration event ID, so retry cannot
invent another identity. Delivered records stay retained and consume capacity.

The bus clones every payload before handing it to a consumer. A test clears one
consumer's bytes, returns failure, and verifies the next sees its own original
copy. Missing subscriptions fail rather than silently acknowledging publication.
Rust reports the first failure; it does not reproduce Go joined-error identity.

Dispatcher owns one serialized batch, up to 32 messages, each under two seconds.
Success acknowledgment is separate from consumption and can be lost. Five
failed attempts exhaust a retained record and readiness fails. Worker cancellation
does not count as a failed attempt. The [composition test](../../../../cmd/server/tests/audit_delivery.rs)
loses one acknowledgment after real Audit commit, then observes stable event ID,
one entry and unchanged first recording time after retry.

Worker stop/join behavior is explained in
[owned polling](../../../language/owned-polling-and-cancellation-safe-join.md).
Paused-time tests observe timeout cleanup and cancellation without sleeping.
Gotchas: no global source order, exactly-once execution, durable restart or flush
on exit is promised. Do not use this dispatcher to replay arbitrary HTTP commands.
Verification: `make rust-test`, `make backend-audit-parity-test`.
PostgreSQL follow-up: producers now supply owned lease SQL to the
[shared lease mechanics](../persistence/postgres/README.md). Durable acceptance
checks takeover, stale-owner refusal, five-attempt exhaustion and blocked successor
streams. Next: how should a broker adapter preserve event IDs and inbox semantics?

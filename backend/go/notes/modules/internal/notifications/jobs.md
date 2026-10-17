# Queued preview walkthrough

Follow-up: [owned recovery](recovery.md) now adds cancellation before Begin and
explicit linked replacement after eligible terminal outcomes. The worker and
original sending/uncertainty boundary below remain unchanged.

Claim: Notifications owns the durable intent and send decision while a generic
worker supplies only polling and shutdown. Origin: queue/recovery slice, 2026-10-17.

## Read the source

1. [Domain state](../../../../internal/notifications/domain/push_job.go) owns
   immutable intent identity, execution lifetime, retry limits and claim fencing.
2. [Application ports](../../../../internal/notifications/app/push_jobs.go),
   [enqueue](../../../../internal/notifications/app/command/queue_push_preview.go)
   and [lookup](../../../../internal/notifications/app/query/get_push_job.go)
   keep persistence and transport outside the use cases.
3. [Execution](../../../../internal/notifications/app/command/run_push_job.go)
   wraps the existing dispatch handler with a gateway that commits Begin first.
   The same Identity capability and exact target rechecks remain in effect.
4. [Memory](../../../../internal/notifications/infra/memory/push_jobs.go) and
   [PostgreSQL](../../../../internal/notifications/infra/postgres/push_jobs.go)
   implement atomic replay/claim/begin/finish. SQL touches only Notifications.
5. [HTTP](../../../../internal/notifications/transport/http/v1/push_jobs.go)
   proves auth/proof/current binding and projects eight explicit public fields.
   The module selects [generic polling](../../../../pkg/jobs/worker.go) only
   when requested; shutdown stops it before closing pools.

## Mechanics and checks

Go embeds expected failures in ordinary error returns. The gateway decorator sets
its local started flag only after a successful store Begin. If acknowledgement is
lost after that write committed, finishing as an unstarted claim is fenced out;
lease recovery records uncertainty instead of queueing another send. Settlement
uses WithoutCancel plus a fresh two-second timeout so cooperative shutdown can
record its result without detaching provider work indefinitely.

[Conformance and crash checks](../../../../tests/push_jobs_test.go) run against
memory and disposable PostgreSQL; [HTTP checks](../../../../tests/push_jobs_http_test.go)
exercise real Identity plus an injected gateway. The first PostgreSQL run exposed
location-sensitive time.Time equality: scanning timestamptz did not retain the
same location representation as memory. Restore now canonicalizes all times to
UTC/microseconds, matching the existing domain convention. Database failure was
not treated as an absent job or a reason to erase evidence.

Read [marker reasoning](../../../patterns/send-markers-and-conservative-recovery.md)
and [setup/exact execution evidence](../../../../../../docs/blueprints/push-jobs.md).
The queue has no fanout, broker, administrative redrive or retention pruning.
Its conservative outcomes do not establish live provider/device delivery.

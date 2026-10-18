# Owned serial polling through a runner port

Claim: a polling worker can own admission and task lifetime while leaving retries
and settlement to the capability that understands the external effect.

Origin/evidence: 2026-10-18 Rust 1.86/Tokio 1.53 implementation and
[lifecycle execution](../../../../pkg/jobs/src/tests.rs). Read
[the source](../../../../pkg/jobs/src/lib.rs), then
[Notifications' runner](../../../../internal/notifications/src/app/command/run_push_job.rs).

Runner returns a borrowed PortFuture with a Scope. Worker owns one Tokio task,
one cancellation flag, watch stop signal, interval and health guard. Its loop
waits for a tick, admits one run and awaits completion before another tick can
start work. Missed ticks are skipped; they do not create parallel catch-up work.
No business crate, provider SDK, SQL or retry vocabulary enters this package.

Example (Conceptual): shutdown arrives while a provider future is active. Stop
cancels the Scope and wakes idle polling, but does not select/drop the active
runner. The runner's bounded send then settles under a fresh two-second scope.
Only when it returns does the worker finish. An outer select that drops the run
on stop could bypass that settlement and detach uncertainty from lifecycle ownership.

Close borrows the retained JoinHandle while awaiting it, then consumes it only
after completion. If the close future is canceled, another caller can still join.
The test waits for an active runner to observe cancellation, drops its first close
future, releases settlement and verifies a second close joins the work and clears
health. Drop requests stop and abort as a fallback; it cannot synchronously await
or promise the runner completed. Process composition must explicitly close before
module resources/pools. See [owned polling/joining](../../../language/owned-polling-and-cancellation-safe-join.md).

Gotchas: cooperative Scope checking cannot interrupt blocking code. A custom
runner must provide its own bounds; this generic worker cannot invent them. The
Notifications runner bounds claim, existing dispatch and independent settlement.
Their completion can exceed the HTTP-only drain budget. Worker health detects a
stopped/panicked task; it is not a claim that every provider attempt succeeded.
Errors report only Kind through the selected callback. Related:
[job ownership](../../internal/notifications/jobs.md),
[marker boundaries](../../../patterns/durable-send-markers-and-explicit-replacement.md).

# Serial polling worker

Claim: execution cadence and cooperative lifecycle can be reusable without owning
business retries or durable state. Origin: queued preview slice, 2026-10-17.

Read [worker](../../../../pkg/jobs/worker.go) and
[lifecycle test](../../../../pkg/jobs/worker_test.go). Start admits one run loop;
each tick awaits Run before another tick executes. Close cancels its context and
waits for the in-flight cooperative run. Double start/restart after close fail;
close is idempotent. An optional OnError hook observes run failures; the worker
itself does not persist, classify or replay them.

The mutex protects lifecycle transitions rather than holding a transaction across
business work. The channel closes when the goroutine exits, making Close a drain
boundary. The owning module must stop this worker before closing the dependencies
used by Run. Non-cooperative code can still delay shutdown; the worker does not
pretend a canceled context forcibly terminates a goroutine.

Notifications uses this mechanism with its own fenced jobs. Read
[the module walkthrough](../../internal/notifications/jobs.md) and
[context cancellation](https://pkg.go.dev/context#WithoutCancel). The test observes
serial cancellation/drain and lifecycle refusal; it is not a durable store test.

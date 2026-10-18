# Owned polling and cancellation-safe joining

Claim: a background poller remains owned only if its stop signal and join handle
remain reachable throughout shutdown, including when shutdown itself is canceled.

Origin/evidence: 2026-10-18 [Worker implementation/unit test](../../pkg/events/src/dispatcher.rs),
[paused-time delivery tests](../../pkg/events/tests/delivery.rs) and real API
SIGTERM/listener cleanup. This uses Tokio 1.53.2 on Rust 1.86.

A watch channel carries the stop signal; the worker selects that signal against
both its polling tick and current dispatch future. A shared atomic Control makes
cooperative store checks see cancellation before commit. The task owns its
future/guards, so stopping dispatch drops outstanding work without spawning a
replacement or consuming an attempt merely for shutdown.

The important detail is awaiting the handle by mutable reference. Taking the
handle out of self before await would let a canceled close future drop it and
detach the task. Retaining it in self permits another close or Drop's abort.

```rust
// Conceptual: keep task ownership while a potentially canceled await is pending.
let result = if let Some(task) = self.task.as_mut() {
    task.await.map_err(|_| worker_failed())
} else {
    Ok(())
};
self.task.take();
```

The source uses its owned Failure constructor in place of worker_failed. The unit
test polls close once, cancels it, proves the handle remains, then aborts/joins
and verifies the completed error is consumed once. Delivery tests separately
observe an in-flight Drop guard, unchanged attempt count and stopped health.

Process shutdown first fences every worker, then joins every worker, then closes
Identity crypto. Drop's abort is a fallback, not an async join or flush guarantee.
No blocking KDF is moved under this worker; its different ownership remains in
[the crypto note](../substrate/argon2-and-blocking-lifetime.md).
Gotchas: an async deadline cannot preempt synchronous code that never yields.
Cancellation after an external effect still does not establish rollback.
Run `make rust-test`. Related: [event mechanics](../modules/pkg/events/README.md).
The PostgreSQL composition now joins these workers before pool closure; a crashed
process leaves leases for expiry/takeover rather than inventing delivery success.
Next: how should a broker adapter retain the same owned shutdown boundary?

Recovery follow-up, 2026-10-18: [ResetQueue](../../internal/identity/src/infra/reset_queue.rs)
uses the same ownership rule with a Tokio Mutex around its optional JoinHandle.
Close awaits `slot.as_mut()` while retaining the handle in the owner; it removes
the completed handle only after await. The mutex also serializes concurrent close
calls. [Queue acceptance](../../internal/identity/tests/reset_queue.rs) holds an
async Mailer, fills the remaining 64 slots, refuses overflow, then observes the
held future dropped and the worker joined after canceling an initial close await.
Startup returns a Failure without a
Tokio runtime instead of panicking. Process composition fences this queue with
delivery workers before draining KDF jobs or closing pools. This does not make
accepted queue entries durable, or make canceled external effects roll back.

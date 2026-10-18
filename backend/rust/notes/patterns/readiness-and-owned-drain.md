# Readiness and owned drain

Claim: process shutdown should stop new admission while giving owned in-flight
work a bounded opportunity to finish before its dependencies are released.

Origin/evidence: 2026-10-18 real loopback tests for active-request completion,
readiness closure and forced task cleanup; source inspection of Go's existing
server lifecycle. Tokio's [shutdown guidance](https://tokio.rs/tokio/topics/shutdown)
separates detecting shutdown, notifying tasks and waiting for completion.

The server holds connection tasks in a JoinSet and a shared serving flag.
Shutdown first clears the flag, closes the listener and notifies connections to
drain. It awaits the tasks under a separate deadline. On timeout it aborts and
joins them before returning failure. The process signal does not directly cancel
each active request. A database profile must stop workers before pools
and preserve write uncertainty; connection closure is never proof of rollback.

```rust
// Excerpt: bounded drain expires before explicit abort and join.
connections.abort_all();
while connections.join_next().await.is_some() {}
```

The full implementation and its source-owned task tests are linked from the
[server walkthrough](../modules/internal/server/README.md). The distinction
between notifying and joining matters: requesting cancellation alone does not
establish that cleanup or task destructors have run.

Gotchas: cooperative cancellation cannot interrupt blocking code that never
yields. Dropping a task may release local memory while an external request has
already committed. A lock or pool must not be destroyed under unresolved work.
This slice has no database pools, business workers or spawn_blocking work.

Used in the Rust server and its Go behavioral comparison. Related:
[behavioral parity](../concepts/behavioral-parity.md). Next: how should a module
report that shutdown completed but a commit acknowledgement is still uncertain?

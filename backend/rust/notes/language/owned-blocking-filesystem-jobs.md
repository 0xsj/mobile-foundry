# Owning blocking filesystem work across cancellation

Claim: moving disk I/O to spawn_blocking requires a retained job owner because
dropping its awaiting request cannot stop a running blocking operation.

Origin/evidence: 2026-10-18 Rust 1.86/Tokio 1.53.2
[Directory implementation and tests](../../internal/files/src/infra/objects.rs),
real directory reopen/corruption checks and process shutdown. API reference:
[Tokio spawn_blocking](https://docs.rs/tokio/1.53.2/tokio/task/fn.spawn_blocking.html).
The locked cached APIs were inspected and compiled; this is not a disk-latency
or forced-interruption guarantee.

Directory admits four jobs through owned semaphore permits. Each blocking closure
owns its bytes, permit and temporary-file cleanup guard. A oneshot channel returns
the result to the request; Directory separately retains the JoinHandle. If the
request is canceled, sending the result may fail but the owner can still join the
job before closing process resources. This keeps blocking disk calls outside
the async executor while retaining shutdown ownership.

A subtle close rule matters when several handles are stored:

```rust
// Excerpt: remove each completed join before awaiting another potentially pending job.
while let Some(job) = jobs.last_mut() {
    if job.await.is_err() {
        self.failed.store(true, Ordering::Release);
    }
    jobs.pop();
}
```

Awaiting all handles and clearing the vector afterward is unsafe under cancellation:
a second close could poll a handle whose result was already consumed. Retain the
currently pending handle, remove completed ones immediately, and serialize close
through the same async mutex. New submissions recheck the admission fence under
that guard. Finished unpolled jobs can be reaped before new admission.

Tests hold a real blocking closure, cancel its caller, observe the permit retained,
start close and prove it remains pending until work is released. Another test
finishes one job, cancels close while another is pending, then successfully resumes
close without repolling the completed handle. Run make rust-test. Directory
integration checks also retain bytes across close/reopen and reject corruption.

Gotchas: started blocking jobs cannot be aborted like cooperative async futures.
A slow filesystem can outlast the HTTP drain deadline. Cancellation after a hard
link/unlink does not prove rollback; temporary-file guards and exact command
recovery have different responsibilities. Normal process shutdown explicitly joins
before pools; SIGKILL relies on persisted metadata/bytes and later product recovery.
Related: [KDF lifetime](../substrate/argon2-and-blocking-lifetime.md),
[async worker joining](owned-polling-and-cancellation-safe-join.md) and
[Files walkthrough](../modules/internal/files/README.md).
Next: how should an explicit object-store shutdown deadline report still-running I/O?


# Known rejection and unconfirmed cleanup

Claim: an external result and its local cleanup are separate facts, so failed
cleanup must not erase a known rejection or trigger another external attempt.

Origin/evidence: 2026-10-18 [Rust dispatch](../../internal/notifications/src/app/command/send_push_preview.rs),
[outcome types](../../internal/notifications/src/app/push.rs),
[controlled timeout/lost-ack tests](../../internal/notifications/tests/push.rs)
and PostgreSQL 14.24 rollback acceptance, mirroring Go's attempt-plus-error result.

A provider definitively rejects address A. The application then asks its own
store to retire the original owner/revision. That write may fail, time out, or
commit while its acknowledgement is lost. Flattening all of this into an outer
error discards the already-known rejection. Retrying the whole command would
send again just to learn whether cleanup finished.

Rust can carry both facts through an enum with a nested result:

```rust
// Excerpt: the application outcome separates dispatch from cleanup confirmation.
pub enum PushAttempt {
    Accepted,
    InvalidTarget { retirement: Result<bool, Failure> },
}
```

The outer Result<PushAttempt, Failure> reports failure to establish a definitive
disposition. Inside InvalidTarget, Ok(true) confirms matching cleanup changed
state; Ok(false) confirms no matching enabled revision changed; Err means cleanup
is unconfirmed, even if it committed. The enum makes acceptance with a cleanup
error an unrepresentable combination. A single Result around the whole sequence
would lose the rejection unless every cleanup failure carried another custom
partial-result error type.

For example, the store commits disable/token erasure, then the caller loses the
acknowledgement. InvalidTarget retains the timeout. Repeating only guarded cleanup
returns false because the revision is already disabled. The gateway call count
remains one. If the token rotated during send, guarded cleanup also returns false,
but it preserves the newer enabled revision rather than unlinking the device.

The timeout boundary matters too. One outer timeout around send plus cleanup
could discard the rejection when cleanup stalls. Dispatch instead reuses one
absolute monotonic deadline across phase-local waits. A cleanup timeout becomes
the nested error; an uncertain send timeout stays an outer error. No phase gets
a fresh five-second allowance, and no future is detached to continue after return.

Actual use/checks: [shared scenarios](../../../../contracts/fixtures/backend-push/attempts.json)
assert rejection plus failed cleanup in both languages. Rust tests use paused
time for total/capability budgets, cancel after rejection, and inject a lost
cleanup acknowledgement. PostgreSQL rejects the cleanup write, checks retained
reservation/token, then reconciles cleanup without another send. These are not
real COMMIT-wire-loss or cloud-delivery tests.

Gotchas: callers must inspect the nested result. No durable storage or automatic
reconciliation is implied; a future queue must persist send knowledge separately
from cleanup progress. A timeout cannot undo a remote side effect. Related:
[module walkthrough](../modules/internal/notifications/push.md),
[rebinding/tombstones](rebinding-and-retirement-tombstones.md),
[owned async ports](../language/results-and-owned-async-ports.md).
Next: which facts must a durable job commit before allowing a provider call, and
how should a crash after that marker preserve duplicate-delivery uncertainty?

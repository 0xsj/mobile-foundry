# A marker records uncertainty, not atomic delivery

Claim: retry safety changes at the first durable point that authorizes an external
effect, and recovery must preserve that boundary even when its acknowledgement is lost.

Origin/evidence: 2026-10-18
[Rust Notifications](../modules/internal/notifications/jobs.md), common Go/Rust
transitions, injected acknowledgement loss, PostgreSQL rollback and an owned
SIGKILL after a committed marker plus simulated file effect.

Before sending, a claimed job has a random token and lease. The exact token can
commit sending; only confirmed success permits the provider call. A later worker
cannot know whether the original process died just before the call, after the
provider accepted it, or while persisting its result. All three recover as
uncertain rather than queued. A marker acknowledgement failure similarly refuses
the call, while persisted fencing prevents the attempted unsent settlement from
rewinding a committed marker.

Example (Conceptual): source S is uncertain. A recovery action creates child C,
retaining retry_of=S and accept_possible_duplicate=true in its immutable intent.
One direct child per source prevents two simultaneous recovery requests from
silently multiplying work. Replaying C returns it; choosing another child or
changing its acknowledgement conflicts. S remains unchanged, including uncertainty.

Cancellation competes with Begin on the same owner guard. If cancellation wins,
the old claim cannot begin or settle. If Begin wins, cancellation conflicts rather
than implying that the provider request was retracted. Children/descendants are
separate intents and are never canceled implicitly.

Use this pattern for bounded external effects that lack a transaction with your
database or a dependable provider receipt. It deliberately permits missed effects
and explicit duplicate-risk decisions. It does not establish exactly-once behavior,
safe automatic redrive, retention policy or administrative access. Related:
[known rejection/cleanup](known-rejection-and-unconfirmed-cleanup.md),
[logical deletion before cleanup](logical-deletion-before-object-cleanup.md).

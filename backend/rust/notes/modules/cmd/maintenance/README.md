# Explicit bounded Identity pruning

Claim: a maintenance command can reuse an owning module's retention operation
without starting the full API, crypto or mail runtime.

Origin/evidence: 2026-10-18 Rust 1.86 and the retained Identity RecoveryStore port.
Read [the command](../../../../cmd/maintenance/src/main.rs),
[Identity facade](../../../../internal/identity/src/lib.rs) and
[store pruning](../../../../internal/identity/src/infra/postgres/recovery.rs).

The process admits an explicit --limit from 1..1000, default 128, and requires
PostgreSQL with owned Identity. It opens the configured Identity pool, checks
the local immutable history and invokes the facade under a fifteen-second scope.
The module selects its own adapter; the command does not query business tables or
construct the full Identity module. Pool close follows the result on failure too.

Example (Conceptual): invoke maintenance --limit 128 from the tools profile. It
reports only a count of expired challenges removed. A live challenge is retained;
the command supplies no general Audit/receipt/session erasure policy.

Verification: argument tests refuse zero, overflow, negatives, unknown flags and
non-numbers. Existing store conformance covers expired/live challenge behavior;
Docker acceptance runs the actual command against the restored database. A
dropped write/timeout is not proof that no pruning committed. No periodic worker
or broad product retention is selected.
Related: [portable profile](../../infra/production/README.md).
Next: which retention policies need their own domain-specific approval and evidence?

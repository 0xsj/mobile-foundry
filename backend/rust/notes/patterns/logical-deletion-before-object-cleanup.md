# Logical deletion before object cleanup

Claim: metadata must make a file inaccessible before an external deletion can
remove its bytes, while the original command remains available for cleanup retry.

Origin/evidence: 2026-10-18 Files commands, shared memory/PostgreSQL conformance,
cleanup-failure injection and actual Go/Rust restart handoff.

[DeleteFile](../../internal/files/src/app/command/mod.rs) commits a tombstone,
required lifecycle fact and optional receipt through Store, then invokes Objects.
That ordering ensures a cleanup failure leaves inaccessible bytes instead of an
available record whose bytes were already removed. It cannot create a transaction
across the database and filesystem.

For example, delete expected v2 with key K commits v3 but cleanup returns unavailable.
Reusing K and the original input obtains the historical v3 result before current
existence/CAS and repeats cleanup. Generating another key or changing expected to
v3 invents another command and cannot prove the original operation's outcome.
A receipt query is observational and never performs that external effect.

Completion uses the opposite prerequisite: verify immutable stored bytes before
committing availability. A keyed completion replay returns before checking objects,
so a later deletion does not destroy its historical evidence. That evidence does
not authorize downloading a deleted file. Download/upload owners recheck metadata
after byte work to cover ordinary races; a crash can still leave orphan bytes.

[Store conformance](../../internal/files/tests/support/mod.rs) injects one cleanup
failure, reads the committed receipt, refuses current metadata/download and retries
the original key. It then replays the older request/completion without another fact.
make backend-files-parity-test and make backend-postgres-parity-test also verify
actual HTTP and process-restoration behavior.

Gotchas: unkeyed deletion has no durable receipt to recover through. Logical
inaccessibility does not attest physical erasure, and receipt retention needs its
own policy. Automatic sweep/retry is not selected. Related:
[historical receipts](historical-receipts-before-current-state.md),
[Files walkthrough](../modules/internal/files/README.md) and
[owned I/O](../language/owned-blocking-filesystem-jobs.md).
Next: what evidence would an explicit orphan sweeper require before deleting bytes?


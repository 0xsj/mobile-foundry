# Quiesce writers before a database/object pair snapshot

Claim: sequential backups of two stores can describe one restorable state only
when the selected writers are stopped across the entire snapshot interval.

Origin/evidence: 2026-10-18 portable API backup/restore execution, with metadata
in PostgreSQL and immutable private file bytes outside it.

What and why: a consistent database dump alone cannot capture bytes concurrently
uploaded or deleted in a separate filesystem. Stop API admission, drain sockets/
workers and finish owned resources, then capture database and object directory
while the API stays stopped. Restart only if it was running before the operation.
Required command receipts/security evidence remain paired with their private state.

Example (Conceptual): a completed file row and its content are backed up together.
Restore into a fresh private namespace, verify histories and read the bytes and a
historical receipt through the real API. Retain a pending challenge too, consume
it after restore and prove single-use plus session revocation still work.

Gotchas: external writers, split databases or cloud deletion workers invalidate
this quiescence assumption unless explicitly included. Never restore over a used
project/volume. Verify checksum and safe archive ownership/types/paths before
creating resources. A manifest published last identifies a completed owned bundle;
it does not make a failed partial destination safe to overwrite. Checksums detect
corruption but do not authenticate an archive. Restored sessions can still be active.

Used in: [operational drivers](../modules/scripts/README.md) and
[portable profile](../modules/infra/production/README.md). Evidence is local Docker
recovery, not replicated failover, PITR or hosted disaster-recovery timing.
Next: which product-specific retention/erasure decisions must survive a restore?

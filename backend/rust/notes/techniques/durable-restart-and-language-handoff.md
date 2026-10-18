# Durable restart and language handoff

Claim: a database-backed API is better tested by interrupting real process
ownership and replaying committed input than by asserting only repository CRUD.

Origin/evidence: 2026-10-18 isolated PostgreSQL 14.24 run, Rust 1.86/SQLx 0.8.6
and the current Go core. This is observed subset compatibility, not full parity.

The test owner creates a fresh database cluster or disposable container and
passes only its URL to child APIs. It clears inherited application settings,
owns each listener/process and removes its namespace after stopping everything.
It never accepts the developer's app URL or resets their Compose volume.

Example (Conceptual): temporarily refuse the Audit inbox insertion, create and
rename an account, and kill the Rust producer after both responses. Remove the
refusal and start Go against the same owned database. Assert the account version,
session credential and original outbox event IDs survive. Then remove producer
acknowledgments after Go has recorded evidence, restart Rust and assert replay
preserves exact Audit entries, sequences and recording times. A fingerprint
encoding difference now fails instead of silently appending different evidence.

Repository checks complement this process test: inject outbox/inbox constraints
to force rollback, race bootstrap and CAS, expire a lease to reject stale-owner
acknowledgment, and hold the Audit append lock to inspect cursor visibility.
Test databases have no unrelated traffic, so deliberate fault constraints and
acknowledgment loss are owned test operations.

Gotchas: passing a process restart does not prove what happened when a real
database connection dies during COMMIT. Matching core migrations does not permit
starting a subset implementation on newer unsupported events. Resource cleanup
must run on test failure too, and private values must not enter assertion/log
output. Used in [PostgreSQL walkthrough](../modules/pkg/persistence/postgres/README.md)
and [migration artifacts](../modules/migrations/README.md). Next: independently
exercise a generated Rust starter before calling it product-ready.

Recovery follow-up, 2026-10-18: the disposable runner now stages a verification
challenge in Rust, captures its private code locally and kills the API. Go opens
the same owned database, verifies that code and stages a password reset. Rust
then consumes Go's reset, refuses the old session/replayed code and signs in with
the new password. Six selected Audit entries eventually appear without duplicates.
This checks persisted purpose/digest/checkpoint/revision compatibility rather than
equating raw independently random tokens. Outbox-constraint fault tests separately
prove verification/reset rollback leaves the same code usable after the fault is
removed. See [recovery learning notes](../modules/internal/identity/recovery.md).

Installation follow-up, 2026-10-18: fail the consumer marker write and kill Rust
after Identity commits sign-out. Go reads the same proof-protected registry and
explicitly rebinds one installation before delivery resumes. Recovery retires
only the old binding. Then kill Go with a pending password-change v2 source;
Rust consumes it, rebinds the installation and replays the same marker without
clearing the new target. Database constraints separately prove cleanup/tombstone/
marker and expired-target claim rollback. Six separate databases exercise the
registry without Identity tables in Notifications' database. See
[installation ownership](../modules/internal/notifications/installations.md).

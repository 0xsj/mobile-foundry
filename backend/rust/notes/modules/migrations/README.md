# Local migration artifacts and compatible history

Claim: explicit process ownership can select durable adapters without moving SQL into
business policy or foreign module repositories.

Origin/evidence: 2026-10-18 Rust 1.86/SQLx 0.8.6, PostgreSQL 14.24.
Source reading order:

1. [README.md](../../../migrations/README.md): Provenance and scope: byte-for-byte Go histories, embedded locally without Go build references.
2. [lib.rs](../../../migrations/src/lib.rs): Catalog: static Schema/Migration values and include_str! for each owned SQL file.
3. [migrations.rs](../../../pkg/persistence/postgres/src/migrations.rs): Application: schema/history ownership, checksums, future-history refusal and partial-run semantics.

Example: run the migrator against an unused database, start the API, commit an
account rename and stop the process. Its version and stable event ID survive;
independent Audit may catch up after the next process starts. A missing migration
refuses startup instead of silently creating tables.

Verification: `make backend-postgres-parity-test` from the repository root owns a
disposable database, tests file/history rollback, transaction races and replay,
then exercises real Rust/Go process handoff. `make rust-check` checks formatting,
Clippy and build. Unit workspace tests ignore database acceptance; the explicit runner supplies
the disposable URL/marker and runs it. An ignored test is not durability evidence.

Gotchas: migration presence alone does not implement a workflow. Recovery now
uses existing Identity 0002/Audit 0004 through separately verified adapters;
Files now embeds its own byte-for-byte Go history and admits File Audit evidence
through a separate slice. No applied migration bytes changed.
Notifications owns in-app adapters using 0001 and installation/retirement adapters
using unchanged 0002. Go's 0003/0004 push artifacts retain history compatibility
without selecting those later Rust features. The process opens six owner pools
in explicit identity mode.
No foreign SQL, automatic migration, transaction retry, failover guarantee, backup
profile or generated Rust starter is established. An interrupted COMMIT remains
uncertain.

Related: [SQLx lifetime](../../substrate/sqlx-transactions-and-pool-lifetime.md), [borrowing executors](../../language/borrowed-transaction-executors.md) and [restart/handoff technique](../../techniques/durable-restart-and-language-handoff.md).

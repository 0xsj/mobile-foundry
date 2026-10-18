# Explicit migration command

Claim: explicit process ownership can select durable adapters without moving SQL into
business policy or foreign module repositories.

Origin/evidence: 2026-10-18 Rust 1.86/SQLx 0.8.6, PostgreSQL 14.24.
Source reading order:

1. [main.rs](../../../../cmd/migrate/src/main.rs): Process entry: validated configuration, apply versus read-only --check, per-owner deadline and explicit pool close.
2. [lib.rs](../../../../migrations/src/lib.rs): Selected artifact catalog: Account/Audit, plus Identity when configured; no business repository imports.
3. [migrations.rs](../../../../pkg/persistence/postgres/src/migrations.rs): Per-file transaction and checksum/history checks under an advisory lock.

Example: run the migrator against an unused database, start the API, commit an
account rename and stop the process. Its version and stable event ID survive;
independent Audit may catch up after the next process starts. A missing migration
refuses startup instead of silently creating tables.

Verification: `make backend-postgres-parity-test` from the repository root owns a
disposable database, tests file/history rollback, transaction races and replay,
then exercises real Rust/Go process handoff. `make rust-check` checks formatting,
Clippy and build. Unit workspace tests ignore database acceptance; the explicit runner supplies
the disposable URL/marker and runs it. An ignored test is not durability evidence.

Gotchas: copied additive recovery/File DDL does not implement those Rust actions.
No foreign SQL, automatic migration, transaction retry, failover guarantee, backup
profile or generated Rust starter is established. An interrupted COMMIT remains
uncertain.

Related: [SQLx lifetime](../../../substrate/sqlx-transactions-and-pool-lifetime.md), [borrowing executors](../../../language/borrowed-transaction-executors.md) and [restart/handoff technique](../../../techniques/durable-restart-and-language-handoff.md).

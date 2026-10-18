# Owned PostgreSQL histories

Account, Audit, Identity, Notes, Files and Notifications histories are copied byte-for-byte from the matching
Go migration directories as of 2026-10-18. The migration catalog embeds these
local files, so a Rust build needs no Go checkout. Repository acceptance compares
the copies with Go before running either implementation. Applied history stores
the SHA-256 of the exact bytes; never edit an applied file.

The catalog belongs to process/migration composition. Business crates own their
SQL adapters and never import this catalog or a foreign module's migration SQL.
Notes owns record/receipt bytea state and its deferred completeness guard.
Each module has its own pool, schema and `schema_migrations` table, even when
all URLs point to the same database.

The copied Audit history includes additive File/recovery columns and Identity's
history includes recovery tables/revision. Their presence preserves migration
checksums and record interoperability. Recovery now uses those existing tables
and Audit columns through separately verified adapters. Files now uses its own
copied history and admitted Audit file evidence. Other
unsupported Audit actions still fail restoration. Starting Rust on a database
with unsupported Go features/events is outside this profile.

Notifications uses 0001 for its in-app decisions/items/preferences and unchanged
0002 for session-bound installations, retirement tombstones and source markers.
0003/0004 retain push history checksums for Go handoff; Rust does not select or
mutate those unsupported push workflows.

Run `make migrate` or `make migrate-check` from the Rust root with explicit
PostgreSQL configuration. Startup only checks history; it never applies SQL.
Read [behavior and limits](../../../contracts/behavior/rust-postgres.md) and
[migration walkthrough](../notes/modules/migrations/README.md).

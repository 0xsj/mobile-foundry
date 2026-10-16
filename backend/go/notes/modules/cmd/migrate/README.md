# Explicit schema deployment

A process-owned migrator can select module deployment artifacts without putting
that inventory into reusable persistence code or business handlers.

Origin: PostgreSQL slice, 2026-10-13. Read
[command](../../../../cmd/migrate/main.go),
[embedded artifacts](../../../../migrations/embed.go),
[generic runner](../../../../pkg/persistence/postgres/migrations.go) and
[startup selection](../../../../cmd/server/main.go).

`cmd/migrate` validates the environment once, requires PostgreSQL selection,
loads each owner's files and opens/closes its pool. `--check` reads histories;
normal mode applies ordered checksummed files. Account and Audit deploy
independently, even when they use the same URL. A failed second module does not
undo the first module's completed deployment.

Runnable check after the local setup in [Go README](../../../../README.md):

```sh
make migrate-check
```

The isolated acceptance harness executes the equivalent `go run ./cmd/migrate
--check` after applying schemas. It also observes unmigrated API startup refusal.
API startup does not apply files, and embedded files let binaries run from other
directories. SQL histories remain immutable after deployment; append a new file.
No automatic down migration, reset, CLI product extraction or deployment service
is supplied. Next: how should module upgrades be coordinated with rolling API versions?

Identity follow-up, 2026-10-13: the migrator also selects its owned schema when
Identity's configured URL is present. Audit applies a new principal-evidence file,
preserving existing Account migration checksums and inbox fingerprints. The
database harness checks all three histories before exercising real auth processes.

Related: [persistence](../../pkg/persistence/postgres/README.md) and
[contract](../../../../../../contracts/behavior/postgres-persistence.md).

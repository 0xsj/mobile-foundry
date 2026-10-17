# Explicit bounded challenge maintenance

Claim: retention commands should identify one owner and safe eligible state,
rather than prune unrelated security or replay evidence by age.

Origin: portable operations slice, 2026-10-17. Read
[command](../../../../cmd/maintenance/main.go),
[Identity entry](../../../../internal/identity/maintenance.go) and
[PostgreSQL operation](../../../../internal/identity/infra/postgres/recovery.go).

The command opens configured PostgreSQL, checks Identity's migration history and
deletes only expired challenge rows in a bounded batch. LIMIT plus SKIP LOCKED
avoids waiting on a challenge another transaction is using. The default is 128;
admission permits 1..1000. Running it explicitly through the tool container keeps
deployment scheduling separate from business transactions.

Conceptual: an expired password-reset challenge may be removed, while a command
receipt or session-revocation marker remains necessary for replay/security policy.
The common word retention does not make those records interchangeable.

Container acceptance runs the command; shared recovery cases cover challenge
expiry and consumption. It is not an erasure coordinator, all-history cleanup
or a scheduler. Broader deletion needs product retention policy and explicit
per-owner capabilities. See [operations](../../../../../../contracts/behavior/backend-operations.md).
Next: select a scheduler in the deployed environment and define policy before
adding another class of deletion.

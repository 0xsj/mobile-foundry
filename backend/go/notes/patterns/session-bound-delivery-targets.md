# Session-bound delivery targets

Claim: rebindable device targets need a durable revocation guard as well as an
optimistic version; actor ownership alone does not close reordered cleanup races.

Origin: installation registry, 2026-10-17. Source and observed memory/PostgreSQL
execution are in [the walkthrough](../modules/internal/notifications/installations.md).

## Mechanics and alternatives

The current access credential admits a principal and a stable refresh-session ID.
Renewal changes access secrets while retaining that session ID. A separate
installation proof authorizes handoff of one installation to another admitted
session. Cleanup matches the current owning session and version, so it cannot
unlink a replacement target. Token addresses are neither of those credentials.

Revocation is an independent committed fact. It may arrive after a newer binding,
which must survive, or before a delayed registration, which must be refused. A
per-session tombstone handles the latter without reading Identity's repository.
One transaction owns token erasure, tombstone and source inbox marker.

Conceptual:

```text
Identity transaction → session.revoked outbox
    → Notifications receiver → owned RetireSession command
    → matching token erasure + session tombstone + source marker
```

A direct call from Identity into the registry would couple transaction and
availability decisions. A foreign SQL join would couple storage ownership. The
receiving adapter instead maps the versioned contract into its consumer's input.
The transport can later move while this use case stays owned.

## Tradeoffs and actual use

The foundation serializes registry changes under one memory mutex/PostgreSQL row.
This makes global target uniqueness, expiry reclamation and handoff straightforward;
very busy registries need a deliberate lock-partition design. No network call runs
under the guard. Retained tombstones/markers need an erasure/retention policy before
unbounded production use; memory has explicit capacity failures.

This is eventual cleanup. An external sender must revalidate current authority and
binding before delivery and still define the remaining check/send race. Local-only
credential deletion cannot tell a server to revoke its session. Native installation
storage and offline unlink orchestration therefore deserve their own slice.

Used by [registry adapters](../../internal/notifications/infra/postgres/installations.go),
[Identity receiver](../../internal/notifications/infra/events/identity.go) and
[conformance/fault tests](../../tests/installations_test.go). Tests prove stale
cleanup refusal, pre-registration tombstones, current-target erasure and transaction
rollback. The process harness proves durable replay after a killed API. It does
not exercise APNs/FCM, system permissions or physical device token ownership.

Related: [ports and composition](ports-and-module-composition.md),
[notification decisions](notification-decisions-and-replay.md) and
[the behavior contract](../../../../contracts/behavior/installations.md).
Next: how should the native vault preserve an installation proof across a user switch?

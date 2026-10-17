# Notification decisions and replay

Claim: deduplication must retain a suppressed delivery decision as well as a
successful notification, otherwise changed preferences can turn replay into new work.

Origin: Notifications implementation, 2026-10-17; compared with Bento's
[delivery rules](../../../../../bento/backend/NOTIFICATIONS.md). Bento also has
catalog/audience/channel/retention machinery; this slice deliberately implements
one account-scoped in-app type. See [behavior](../../../../contracts/behavior/notifications.md).

## What and why

The application admits the Files completion fact as EventID, AccountID,
PrincipalID, FileID and OccurredAt. Its stable fingerprint contains those fields
and a versioned namespace, with UTC microsecond time. Preferences and delivery
clock are excluded: retries should compare the same source input after either has
changed. This fixed encoding must not silently change for already stored markers.

Memory holds one mutex. PostgreSQL claims the event marker, then locks the scope
row and decides the preference in the same transaction. A disabled preference
still commits the marker; an enabled preference additionally commits an item and
increments the scope's arrival sequence. A failure rolls everything back.

Conceptual:

```text
media disabled → consume E → marker E, no item
media enabled  → replay E  → same fingerprint → no new work
media enabled  → consume F → marker F + item F
```

An out-of-transaction preference read is simpler but does not establish ordering
against a concurrent setting write. Source timestamps also make poor cursor keys:
an older fact may arrive later. A commit-ordered scope sequence puts each new
arrival at the front without reallocating already visible positions. HTTP keeps
the sequence inside an opaque scope/filter-bound cursor.

## Gotchas and actual use

Per-scope serialization favors clarity at this scale; it can become a hot row for
very busy recipients. List/count use the same guard to describe one page's state,
not a multi-page frozen snapshot. Read timestamps settle; preference assignments
are last-commit-wins. Neither is a durable offline command receipt protocol.

The source may retry after Notifications committed but Audit failed. Each has its
own inbox identity and store; no distributed transaction is required. This does
not make an external push exactly-once. A provider may accept before its worker
records completion, so future outbound delivery needs explicit duplicate semantics.

Actual use: [store contracts](../../internal/notifications/app/ports.go),
[PostgreSQL guard](../../internal/notifications/infra/postgres/store.go),
[conformance/fault tests](../../tests/notifications_test.go) and
[process recovery](../../scripts/postgres_integration.py). Runtime results are
recorded in [starter readiness](../../../../docs/STARTER-READINESS.md); documentation
checks validate links/example labels without executing snippets.

Related: [module composition](ports-and-module-composition.md) and
[metadata/object lifecycles](metadata-and-object-lifecycles.md). Next: durable
outbound queue ownership, installation lifecycle and data retention.

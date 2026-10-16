# Account integration facts v1

Producer: Account. Names: `account.created.v1`, `account.renamed.v1`.
The first independent consumer is `audit.account`; its inbox identity is
consumer name plus event ID.

Required envelope fields:

| Field | Admission |
| --- | --- |
| `id` | Stable canonical nonzero UUID across delivery retries |
| `name` | One of the two names above |
| `producer` | `account` |
| `account_id`, `actor_id` | Canonical nonzero UUIDs; actor is the admitted owner for these commands |
| `aggregate_version` | Positive JSON-safe integer; create 1, rename at least 2 |
| `occurred_at` | Domain transition time, UTC RFC 3339, microsecond precision |
| `metadata` | Object with optional request/correlation tokens of 1–128 ASCII letters/digits or `._:-` |
| `data` | Object `{"account_id":"<uuid>","version":<integer>}` matching envelope scope/version |

Audit rejects unknown data fields, mismatched scope/version, wrong producer/name
and invalid evidence. The payload is bounded to 16 KiB. JSON duplicate members
retain Go's last-key-wins admission; they are not separate facts.

Account names, credentials, private errors and arbitrary request bodies are
excluded. Audit derives action from the admitted name and retains only selected
evidence/metadata; its own recording time and append sequence are consumer-owned.

The producing store writes state and outbox atomically. Delivery is independent
and replayable; there is no global ordering or exactly-once execution guarantee.
Audit evidence and inbox deduplication commit together. Reusing one consumer/event
identity with different admitted evidence is rejected; identical replay succeeds
without another append.

The local memory bus clones the envelope/payload per consumer. One consumer's
failure does not suppress another matching consumer's attempt. Delivery failure
and lost acknowledgment cause bounded retries through the outbox dispatcher.
All storage/delivery markers are memory-only in this slice; process-loss recovery
needs PostgreSQL/outbox/inbox implementations. See
[workflow behavior](../behavior/account-audit.md) for retry/capacity policy.

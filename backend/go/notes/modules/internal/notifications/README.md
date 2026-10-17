# Notifications walkthrough

Recovery follow-up, 2026-10-17: [cancellation and linked replacement](recovery.md)
extends the queue without rewriting a source outcome. Read the exact boolean
admission and same-ID readback paths alongside memory/PostgreSQL races and the
pre-0004 retained-row upgrade check.

Queue follow-up, 2026-10-17: read [job construction](jobs.md) and
[send-marker reasoning](../../../patterns/send-markers-and-conservative-recovery.md).
Immutable intents, guarded enqueue/status, memory/PostgreSQL claims and a module
worker now exist behind explicit selection. Preflight retries and unknown sends
have different recovery rules; no job owns foreign state or a provider token.

Provider follow-up, 2026-10-17: [adapter/HTTP walkthrough](providers.md) now covers
explicit APNs/FCM selection, private credential parsing, fixed provider protocols
and the proof/session/version-guarded preview route. Default runtime remains off.
Local TLS and API acceptance are separate from cloud/device delivery evidence.

Push follow-up, 2026-10-17: read [preview orchestration](push.md) after
[installation ownership](installations.md). It adds exact target snapshots,
versioned Identity eligibility and guarded invalid-target cleanup behind an
injected gateway. No outbound provider, automatic fanout or public send route
is selected. Local/provider-store checks differ from cloud delivery evidence.

Claim: a notification is an owned delivery decision derived from an admitted
committed fact, rather than a direct send from the producer's domain.

Origin: Notifications slice, 2026-10-17. Source inspection and tests are separated
from pending native/push integration in [the construction guide](../../../../../../docs/blueprints/notifications.md).

## Read in this order

1. [Private Item](../../../../internal/notifications/domain/item.go) and detached
   Snapshot. MarkRead returns a new value and preserves the first timestamp.
2. [Application ports](../../../../internal/notifications/app/ports.go),
   [DeliverUpload](../../../../internal/notifications/app/command/deliver_upload.go),
   [ListInbox](../../../../internal/notifications/app/query/list_inbox.go) and
   [MarkRead](../../../../internal/notifications/app/command/mark_read.go).
3. [Files receiver](../../../../internal/notifications/infra/events/files.go)
   validates the contract and maps identifiers to owned app input, without importing
   Files. [Account client](../../../../internal/notifications/infra/capabilities/account.go)
   implements the consumer's access question.
4. [Memory](../../../../internal/notifications/infra/memory/store.go),
   [PostgreSQL](../../../../internal/notifications/infra/postgres/store.go),
   [fingerprint](../../../../internal/notifications/infra/records/fingerprint.go) and
   [migration](../../../../migrations/notifications/0001_inbox_preferences.sql).
5. [HTTP DTOs/cursor](../../../../internal/notifications/transport/http/v1/handler.go),
   [module composition](../../../../internal/notifications/module.go) and
   [provider/HTTP checks](../../../../tests/notifications_test.go).

## Mechanics and alternatives

The receiving consumer has an interface describing only Handle(Delivery). It can
admit local envelopes today or receive broker envelopes later without exposing
another module's aggregate. The durable in-app Store is its replaceable delivery
boundary. A future push sender/queue is a different effect and is not hidden in it.

In PostgreSQL, the event inbox INSERT waits for a competing same-event transaction.
After ON CONFLICT, a new ReadCommitted statement sees that transaction's committed
fingerprint. For a new event, a per-account/principal scope row guards preference
lookup, sequence increment and item insertion. Preferences use the same row guard.
No receipt crosses this boundary: the source event identity is the dedup key.

Conceptual:

```text
Files commit → Files outbox → notifications.files receiver → DeliverUpload
    → source marker + preference decision + optional inbox item (one commit)
```

Reading preferences outside the transaction would permit a stale preference to
race delivery. Recording the marker only when an item is inserted would resurrect
muted events on replay. Publishing push before commit could send a notice for a
rolled-back item. The current implementation avoids those effects by owning only
transactional in-app delivery in this slice. See
[the decision pattern](../../../patterns/notification-decisions-and-replay.md).

## Use, evidence and limits

Used by cmd/server and the full Go CLI selection. Existing Files workers publish
to independent Audit and Notifications consumers; either may replay after the other
has committed. [Process checks](../../../../scripts/postgres_integration.py) force
blocked-consumer recovery after SIGKILL, then verify persisted read/settings state.
The [generated probe](../../../../../../cli/acceptance/go/verify.py) exercises copied
composition through the root Make runner. The construction guide records commands;
starter readiness records observed results.

The sole type deliberately notifies its actor, since upload completion is useful
feedback. It is not a team audience model. Backlogged first deliveries use current
preferences with no age cutoff. Memory is bounded/ephemeral; PostgreSQL retention,
erasure, Realtime, installations and cloud delivery remain open. Native catalog
adapters now consume the contract; read [their recovery pattern](../../../../../../notes/patterns/inbox-refresh-and-setting-uncertainty.md).

Next reading: how should an installation token be rebound after sign-out, and how
should outbound acknowledgements survive uncertain APNs/FCM acceptance?

Installation follow-up, 2026-10-17: continue with [installation ownership](installations.md) for independent proof, versioned session handoff, target uniqueness and atomic revocation tombstones. The registry is backend metadata; native OS token owners and outbound delivery still need separate slices.

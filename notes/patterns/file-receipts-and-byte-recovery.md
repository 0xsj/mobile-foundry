# File receipts and byte recovery

Claim: completion receipts can settle an upload without touching bytes again,
whereas a deletion receipt alone cannot settle external byte cleanup.

Origin: connected Files extraction, 2026-10-19. Source policy is in
[the contract](../../contracts/behavior/connected-files.md); actual checks and
remaining limits are recorded in [construction](../../docs/blueprints/connected-files.md).
This finding follows the existing Files lifecycle, not a new backend guarantee.

## What and why

The database can commit completion before the client receives its response.
If retry always replays request-upload then PUT, a later deletion can make that
PUT fail even though this client's upload already finished. Reading the original
completion receipt first avoids recreating or attempting inaccessible bytes.
An unavailable receipt read is not proof of absence. Only not_found permits
continuing the same exact upload command and immutable byte input.

Conceptual:

```text
PUT bytes → complete commits → reply lost → another client deletes
  → original completion receipt exists → settle; no further PUT
```

Deletion has a different boundary: metadata becomes inaccessible before external
object cleanup. A receipt can exist while cleanup remains incomplete. Recovery
must repeat the same delete so the backend can retry the external step. Keeping
the original version/key is essential: a new delete is a different intent and
may simply see a tombstone or conflict.

## Ownership and memory

One pending transfer per account still permits unbounded memory across many
accounts. The product uses one pending transfer per owner instead. Other scopes
can be browsed but must return to the original scope to settle the operation.
Lists/downloads clear on detach; intent stays private to the owner. Kotlin copies
ByteArray on admission and before dispatch; Swift Data has value semantics.

A photo draft is not an accepted command. A native picker can temporarily move
the app through lifecycle states and rotate the HTTP lease. Staging first, then
accepting Upload photo explicitly, lets the current lease authorize the eventual
write without implicitly retargeting an accepted operation. Scope-keyed view
state cancels old imports on account changes.

## Gotchas and useful limits

A 10 MiB check after PhotosPicker loads Data bounds retained input, not transient
picker memory. Android's bounded stream reader can reject earlier. Neither byte
size alone bounds decoded image memory, so previews create small thumbnails.
MIME declaration/hash checks do not establish content safety or malware scanning.

In-memory intent cannot promise process recovery. The app explicitly tells users
to keep it open; a durable transfer system would need protected staged bytes,
an atomic command journal, receipt reconciliation and cleanup ownership. Reusing
Notes' text journal without that byte lifecycle would hide a missing guarantee.

## Evidence and next questions

Five owner tests per platform cover lost completion without repeated PUT,
same-delete cleanup retry after logical deletion, ignored old scope completion,
stale import/mismatched bytes and duplicate pages/preview clearing. Independent
execution evidence belongs to the construction guide and is updated after runs.
Next: explicit abandonment/review for terminal conflicts, durable staged bytes,
and the [explicit Files + Inbox composition](producer-receipts-and-consumer-views.md).

## When the original command cannot finish

A terminal conflict still retains the original command; it must not be silently retargeted. The product now permits explicit [abandonment of local recovery](explicit-abandonment-is-not-settlement.md) after a consequence review. This releases bytes/key but proves neither remote rollback nor physical cleanup. A fresh page gates any newly accepted action.

## Durable product follow-up — CLI 0.25.0

The generated Files product now uses [atomic disk staging](atomic-byte-staging-before-transport.md) before HTTP. Earlier ephemeral-lifetime observations describe the original extraction/catalog owner. Receipt/cleanup/abandonment distinctions still apply; Forget must now acknowledge exact journal removal before releasing pending state. This remains manual one-item recovery rather than a transfer queue or secure erasure.

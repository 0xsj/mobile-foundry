# Explicit abandonment is not settlement

Claim: releasing local recovery information is a distinct user decision from
proving that a remote operation completed or was undone.

Origin: native Files recovery, 2026-10-19. A retained delete can become impossible
under its original version, or find that another client already removed the file.
Retry must preserve the original intent; indefinite blocking is also unusable.
The [product contract](../../contracts/behavior/connected-files.md) defines the
decision, and [construction evidence](../../docs/blueprints/connected-files.md)
records which generated workflows actually exercised it.

## What and why

Keep three outcomes separate: confirmed completion, retained uncertainty, and
deliberately abandoned local recovery. Abandonment does not change server state.
An upload might already be available. A delete may have committed its tombstone
while physical cleanup remains unfinished. Labeling either case Cancel or Done
would imply a guarantee that the client does not have.

Show the original action/file and the consequence before accepting abandonment.
Keeping or dismissing review preserves the exact input. A subsequent action
needs a fresh observation and explicit acceptance with a new command identity;
never silently substitute the newest version into a retained delete.

Conceptual:

```text
observed v2 → original delete conflicts → retain original key/v2
  → Keep: same pending intent
  → Forget local recovery: discard only local responsibility
      → fresh page → explicitly accept a different action/key
```

## Stale confirmation is another async result

A dialog can outlive session renewal or the operation it describes. Bind its
confirmation to both an owner generation and an immutable operation identity.
Scope equality alone cannot distinguish two operations in the same account.
Reject confirmation during active work. After accepted abandonment, invalidate
other input tickets and require a successful read before new changes.

The confirmation token is a local consistency check, not server authorization.
It contains no upload bytes or credentials. Authorization still belongs to the
current service port when a real command is explicitly accepted.

## Gotchas and limits

- A receipt can prove logical deletion while cleanup still needs the original
  command. Forgetting that command gives up this client's cleanup retry path.
- A missing row in a bounded page proves neither absence nor rollback.
- Forgetting cannot retract an already dispatched request. Generation fencing
  stops late publication; it cannot undo a remote commit.
- Ephemeral retention still loses recovery on process exit. This decision adds
  no durable queue, orphan sweep or automatic conflict resolution.

## Used in and related

The app-owned Swift/Kotlin FilesStore offers review/keep/forget above the existing
repository seam. Unit checks exercise conflicts, old dialogs, active reads and
new command identity; native workflows use an actual competing backend delete.
Follow the linked construction guide for counts and observed execution limits.
Read [file receipts and byte recovery](file-receipts-and-byte-recovery.md) first,
then [producer receipts and consumer views](producer-receipts-and-consumer-views.md)
for why this local decision says nothing about notification delivery.

## Durable product follow-up — CLI 0.25.0

The generated Files product now uses [atomic disk staging](atomic-byte-staging-before-transport.md) before HTTP. Earlier ephemeral-lifetime observations describe the original extraction/catalog owner. Receipt/cleanup/abandonment distinctions still apply; Forget must now acknowledge exact journal removal before releasing pending state. This remains manual one-item recovery rather than a transfer queue or secure erasure.

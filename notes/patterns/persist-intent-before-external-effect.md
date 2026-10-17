# Persist intent before an external effect

Claim: a saved original command makes an uncertain acknowledgement recoverable,
while persistence of the matching result makes recovery itself restartable.
Origin: native pending Notes slice, 2026-10-17; evidence is the shared contract,
real SQLite rollback/reopen checks and injected owner failures. Device/process
acceptance is tracked in the construction guide rather than inferred from those checks.

## What and why

A remote command crosses two independent commit boundaries: local intent storage
and the server. Save exact input before sending. If saving is uncertain, reload
before deciding to send; inventing another command can duplicate the operation.
If the server replies successfully, save its matching receipt before clearing the
pending intent. If that save is uncertain, reload or query the original receipt.
No distributed transaction is required, and none is claimed.

```text
Conceptual: restart can happen at every arrow.
new command -> durable pending -> remote attempt -> durable receipt -> next command
                   |                 |
                   +--- explicit receipt lookup or retry of the same command ---+
```

A missing receipt establishes only that none was observed. The original attempt
may still be in flight. It cannot authorize discarding input or inventing a new
ID. A settled slot can be replaced using its original command ID as a compare
value; delayed work must compare again before settlement. Scope follows the
original deployment, principal and account, independent of a renewed token.

## Gotchas and alternatives

A single slot intentionally blocks another edit while uncertain. It is a useful
starting seam, not a multi-command outbox or conflict resolution policy. Keeping
credentials separate prevents renewed leases from becoming the storage identity.
Private backup-excluded files still contain user content; retention/erasure needs
an explicit product policy. Corrupt storage must not look like an empty queue.

Persistence does not authorize automatic replay. A scheduler, batch ordering,
read cache, expiry, merge policy and user-visible discard each require additional
contracts. Use Memory when transient recovery is intentional; select disk at
composition when process survival is part of the behavior.

## Used in and next questions

Read [pending behavior](../../contracts/behavior/pending-notes.md),
[construction and actual checks](../../docs/blueprints/pending-notes.md),
[Swift Data walkthrough](../../frontend/swift/notes/modules/packages/FoundryData/README.md)
and [Kotlin Data walkthrough](../../frontend/kotlin/notes/modules/project/core/data/README.md).
The connected Daily overlays use the same service port with native disk adapters;
the catalogs retain their earlier transient examples. Next: which product actually
needs more than one outstanding command, and what ordering/erasure policy follows?

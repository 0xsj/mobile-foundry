# Command receipts and uncertain writes

Claim: a caller recovers a lost acknowledgement by retaining the original command
and consulting committed evidence, rather than inventing a replacement command.

Origin: remote Notes, 2026-10-13. Evidence: both native transports deliberately
drop an acknowledgement after real Go commits; provider tests race twelve copies;
PostgreSQL API kill/restart and receipt-finalization rollback tests pass.

## What and why

A timeout describes what the caller observed. It does not describe whether a
transaction committed. A command ID names one exact input within an authority,
principal and account. The server records its result in the same transaction as
the data change, then checks that record before considering a replay's version.
This separates write admission from acknowledgement and from later read success.

A receipt is historical. A create receipt can remain valid after the note has
been edited or deleted. Returning that receipt confirms the earlier command;
running create again would change its meaning. A follow-up list failure likewise
does not invalidate an already confirmed write.

## Example — Conceptual

```text
retain: issuer=A, account=X, command=C, update note=N at version=4
send C → server commits note version=5 + receipt(C), response is lost
lookup receipt(C) under account X → confirms version=5
refresh X → read current data (possibly newer than version=5)
```

If lookup observes no receipt, the original transaction could still be running.
Retrying C with identical input can converge through the store's atomic claim.
Replacing C or its expected version produces a different command and can duplicate
work. Switching to account Y must not retarget the pending write.

## Gotchas

- A header parser supplies no deduplication guarantee. The repository owns the
  claim and atomic commit, and authorization still runs before replay.
- An in-memory pending map survives lease replacement, not process death.
  Durable server receipts and a durable device queue are distinct capabilities.
- Replays must precede CAS/existence checks; a later edit/deletion must not defeat
  acknowledgement of the earlier operation.
- Receipt retention and changed-input comparison are part of correctness.
  Pruning without a defined retry window reopens old commands to duplication.
- Exact text matters. Swift String equality admits canonical equivalence;
  comparing UTF-8 for acknowledgement preserves the command's actual input.

## Used in

The [remote Notes contract](../../contracts/behavior/remote-notes.md) supplies the
concrete protocol; [its setup](../../docs/blueprints/remote-notes.md) supplies the
catalog and acceptance commands. Future native outbox work can reuse command
identity and scope without changing the remote write port. No automatic retry,
offline merge, live signal or production retention policy is established yet.

## Related

[Account partitions and leased work](account-partitions-and-leased-work.md),
[local commits and observation lifetime](local-commits-and-observation-lifetime.md),
[deployment authorities and rotation checkpoints](deployment-authorities-and-rotation-checkpoints.md).

# Recovery preserves the original outcome

Claim: cancellation is a race against the send boundary, while replacing uncertain
work is a new acknowledged intent rather than a correction of its old result.

Origin: owned preview recovery slice, 2026-10-17. Source inspection found that
resetting uncertain to queued would hide a possible earlier effect. Memory and
PostgreSQL tests exercise an explicit child instead, including lost acknowledgements
and competing requests.

## What and why

Before the sending marker, an atomic cancellation can fence the worker out. After
the marker, database state cannot retract the network request. Returning canceled
at that point would invent a stronger guarantee than the system can provide.

A replacement has new identity, a link to the source and immutable acknowledgement
of uncertainty. The source remains useful evidence. One direct child per source
bounds accidental duplicate recovery requests; its same-ID replay remains a
readback even after it settles. Further deliberate recovery targets the child.

Conceptual progression:

```text
queued/leased -- cancel wins --> canceled (old claim fenced out)
sending -- cancellation --> conflict
uncertain -- explicit duplicate acknowledgement --> new linked child
         original uncertainty remains unchanged
```

## Gotchas

An explicit replacement does not prove the earlier request failed. Acknowledging
risk is neither delivery evidence nor account authority. The caller still proves
its current installation owner and active target. Reusing the original session's
job from another sign-in is not implicit retargeting permission.

The child ID, source, target revision and acknowledgement belong to the immutable
intent. After losing a response, changing one is a new conflicting command, not a
retry. Database uniqueness complements application serialization, while retained
lineage also constrains future pruning/erasure policy.

## Used in and related

[Module flow and checks](../modules/internal/notifications/recovery.md) links the
actual commands, stores and tests. Continue from
[send markers](send-markers-and-conservative-recovery.md) and
[explicit JSON booleans](../language/explicit-json-boolean-presence.md).
Next question: how should a product expose retained uncertain outcomes and their
children while maintaining session ownership and a deliberate retention policy?

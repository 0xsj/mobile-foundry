# Subscribe before refetching current state

Claim: registering invalidation before taking the authoritative snapshot closes
the common missed-change window without requiring notifications to carry data.

Origin/evidence: 2026-10-18 implementation and actual socket/HTTP checks in two
backend languages. This is a recoverable invalidation pattern, not durable sync.

What and why: reading a page, then subscribing, leaves a gap in which a write
can commit unseen. Register first, send ready, then read HTTP. A write before
registration is included in the later read; a confirmed write after registration
can leave a pending dirty signal. Duplicates and one-slot coalescing are safe
because a signal asks for current state, not incremental application of an event.

Example (Conceptual): a list subscribes and receives ready. While its page loads,
two writes collapse into one dirty flag. After the load, a queued refetch obtains
the newest page. If a write is already being settled, queue the read behind it;
a hint must not cancel or replay the user's command.

Gotchas: publication is still lossy. Lost commit acknowledgement, process loss,
disconnect and foreign writers can all omit a signal. Refetch on reconnect and
foreground, retain command receipts/pending intent separately, and apply current
authorization to every read. “No hint” is never proof that nothing changed.
Recheck the same connection lease rather than silently upgrading it to a new one.
Multi-replica writers need an explicitly selected cross-process Feed.

Used in: [Realtime](../modules/internal/realtime/README.md) and
[Notes](../modules/internal/notes/README.md). Evidence is live local API execution,
not mobile-device or deployment acceptance. Related:
[historical receipts](historical-receipts-before-current-state.md).
Next: which product events merit durable cursors rather than invalidation?

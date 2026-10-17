# Bounded live hints and authoritative snapshots

Claim: a lossy live signal can make a product responsive while HTTP remains the
owner of authorization, records and reconciliation.

Origin: Realtime slice, 2026-10-17. Evidence: source inspection, hub race tests and
[real socket tests](../../tests/realtime_test.go). See the
[behavior contract](../../../../contracts/behavior/realtime.md).

## What and why

Notes depends on its own Changes port, implemented by a local adapter over the
generic hub. It publishes only after the store returns validated success. A
committed replay may publish again. Realtime depends on its own Access and Feed
ports; its Account capability client never imports Account's implementation.
Neither domain calls the other. This is a different seam from a required fact
that must commit in the producer's outbox and reach independent consumers.

Each connection receives a one-slot dirty channel. Publishing while it is full
coalesces changes instead of blocking the writer or accumulating record history.
On connection, ready requests a refetch regardless of whether a signal exists.
Clients should refetch after changed and on foreground/reconnect, then reconcile
against pending commands using their domain policy.

## Example

Conceptual: A commits a Note, B receives changed and reloads its HTTP page. A
second commit can share the same pending hint. If the API crashes after commit
but before publish, the next connection still sends ready/refetch. A continuously
connected client needs its own refresh policy; delivery is deliberately lossy.

## Gotchas and actual use

The hub bounds subscriptions globally and per principal; it has no durable cursor,
cross-process fanout or presence. The portable profile therefore runs one API.
Revalidation checks the exact bearer/session and account before each frame and
periodically while idle. An affirmative check is point-in-time, not a guarantee
that access cannot change between the check and frame write. Hints carry no
record data. Renewal requires a fresh connection with the new bearer.

Owned socket cleanup is explicit because HTTP Shutdown does not own hijacked
connections. Tests keep a real connection open beyond the normal HTTP budget,
then observe revocation and module Stop. These tests establish backend behavior;
the mobile products do not yet select socket consumers.

Related: [Realtime walkthrough](../modules/internal/realtime/README.md),
[Go contexts](../language/interfaces-errors-and-contexts.md).
Next: add native lifecycle consumers before selecting live hints in a CLI product;
add a broker-backed Feed only when more than one API replica is required.

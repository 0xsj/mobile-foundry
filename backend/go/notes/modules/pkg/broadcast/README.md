# Coalesced in-process invalidations

Claim: one dirty slot per subscriber bounds live-signal memory and keeps writers
independent of consumer speed.

Origin: 2026-10-17 Realtime slice. Read [hub](../../../../pkg/broadcast/hub.go) and
[race tests](../../../../pkg/broadcast/hub_test.go).

Watch counts global and per-owner subscriptions under a mutex. Publish sends
nonblocking empty values only to the matching key. Release and Close remove and
close channels under that same mutex, preventing send/close races. Release is
idempotent, including after Close. No business ID parser, payload or policy enters
this package; the module adapter chooses account keys and principal owner strings.

Conceptual: three changes while one slot is full still mean one pending refetch.
The count of hints is therefore unrelated to the count of committed operations.

Tests cover capacity, owner/key isolation, coalescing and release/shutdown. This
hub is per process and ephemeral; it cannot connect multiple API replicas or
recover a publish missed after commit. See [the hint pattern](../../../patterns/bounded-live-hints.md).
Next: a broker Feed can replace it without putting broker policy into Notes.

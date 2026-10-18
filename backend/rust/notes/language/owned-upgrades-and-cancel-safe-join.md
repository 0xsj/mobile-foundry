# Own upgraded tasks beyond the HTTP request

Claim: moving an upgraded stream out of HTTP handling requires a new task owner
whose join state survives cancellation of a close caller.

Origin/evidence: 2026-10-18 Rust 1.86, Tokio 1.53.2 and Hyper 1.11.1 source inspection
plus real upgrades/canceled-close tests. Tokio documents [JoinSet cancellation safety](https://docs.rs/tokio/latest/tokio/task/struct.JoinSet.html#method.join_next)
and [select branch cancellation](https://tokio.rs/tokio/tutorial/select).
The pinned protocol uses [tokio-tungstenite 0.28.0](https://github.com/snapview/tokio-tungstenite/tree/v0.28.0)
with handshake support; no production hand-written frame codec is introduced.

What and why: async move transfers values into the future; Arc::clone retains
shared ownership without borrowing the short-lived request. Keep only admitted
metadata and exact lease values, and create fresh bounded authorization scopes.
An expired request deadline is inappropriate for a ten-minute socket, but dropping
that deadline does not authorize an unbounded or unauthenticated connection.

Example (Conceptual): an owner stores JoinSet behind a Tokio mutex. Admission
locks it briefly, rechecks stopping, and spawns. close marks stopping and signals
tasks, then locks the same set and awaits join_next until empty. If its caller is
canceled, the mutex guard drops; the set remains inside the owner. A later close
can acquire it and finish joining. Moving the entire set/handle into the canceled
close future would instead lose the intended ownership.

Gotchas: detaching through a framework upgrade callback would hide the handle.
Enable Hyper's upgrade-aware connection driver and retain the upgrade future in
the explicit owner. Stop must interrupt both upgrade wait and socket work. A
biased select has ordering consequences: put stop/deadlines/check ticks before
traffic that a peer can keep continuously ready. That is not general scheduler
fairness. A pending Pong needs its own deadline, not just successful Ping writes.

An unwind wrapper can convert a task defect into owner health failure, but
AssertUnwindSafe is an explicit promise about state boundaries, not proof that
arbitrary partially mutated data is repaired. Keep socket work read-only and
stream-owned, preserve private panic handling, and still join every task. Dropping
a read-only future differs from canceling a business write with uncertain effects.

Used in: [Realtime transport](../modules/internal/realtime/README.md). The tests
retain a deliberately blocked owned task, cancel close, release it and close
again; real sockets separately prove EOF and subscription release on stop.
Related: [owned polling and joining](owned-polling-and-cancellation-safe-join.md).
Next: what additional lifetime owner would streaming response bodies require?

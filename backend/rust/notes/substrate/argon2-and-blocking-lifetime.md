# Argon2 and blocking work lifetime

Claim: a bounded password adapter must own the computation after its async
caller disappears; canceling the caller does not cancel an already running KDF.

Origin/evidence: 2026-10-18 source and execution with Rust 1.86, Tokio 1.53.2,
Argon2 0.5.3, base64 0.22.1, sha2 0.10.9, subtle 2.6.1, zeroize 1.9.0 and
chrono 0.4.45. The Go-generated PHC vector verifies in both adapters; local tests
also verify wrong-password refusal, strict stored parameters, malformed encoding,
token purpose/digest and an owned-close cancellation scenario.

Primary references: [RustCrypto Argon2](https://docs.rs/argon2/0.5.3/argon2/),
[Tokio spawn_blocking](https://docs.rs/tokio/1.53.2/tokio/task/fn.spawn_blocking.html),
[base64 engines](https://docs.rs/base64/0.22.1/base64/) and
[zeroize](https://docs.rs/zeroize/1.9.0/zeroize/).

The selected profile matches Go: Argon2id v19, 19456 KiB, two passes, one lane,
16-byte salt and 32-byte output. Stored parameter strings and decoded lengths
are admitted strictly before computation rather than trusting arbitrary PHC
costs. Wrong passwords compare derived output in constant time. Unknown valid
logins verify a precomputed dummy hash. This preserves policy/work shape; tests
do not claim indistinguishable end-to-end timing or production capacity.

Each calculation first acquires one of two semaphore permits without queueing
unbounded requests, then moves it into spawn_blocking along with the owned
password. The permit is released when the blocking closure actually ends. If
the HTTP future is dropped, the KDF keeps its capacity reservation and its own
secret lifetime. Module close marks admission closed and acquires both permits,
waiting for started or queued calculations to finish after HTTP drain.

The controlled unit test stops a real KDF job before computation using a
test-only channel, aborts its async caller, observes the retained permit and
unfinished close, then releases work and observes close completion. Dropping
the test release sender also unblocks the hook, avoiding an orphaned test job.
This is execution evidence for ownership, not a benchmark or interrupt guarantee.

Gotchas: spawn_blocking work cannot be force-aborted after it starts; the ten-second
HTTP shutdown limit does not bound final KDF completion. Zeroizing clears selected
owned buffers on drop, not every stack/allocator/body copy. The source-IP gate
uses accepted sockets, not forwarded headers, and remains local to one process.
Used in [Identity security](../modules/internal/identity/README.md). Related:
[owned secrets](../language/owned-secrets-and-scope-adapters.md). Next: how should a
future persistence adapter join its own work before closing its resource pool?

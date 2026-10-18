# Shared Realtime cases

`hub.json` contains eight scenarios/38 ordered operations consumed directly by
Go's `parity/realtime_test.go` and Rust's broadcast tests. Scope isolation,
one-slot coalescing, owner/global/zero limits, release/reuse, close and buffered
closure have expected outcomes independent of either implementation.

`http.json` contains 15 native WebSocket admission cases consumed by
`scripts/realtime_parity.py` against real compiled APIs. Both receive actual
upgrade headers. Application-owned problems retain common JSON headers;
library-owned failures/101 are compared only on the captured protocol fields.
The runner's bounded masked-frame client exists only for acceptance, not product use.

Run `make backend-realtime-parity-test`. The runner additionally checks exact
ready/changed fields, confirmed Notes CRUD/replay, failed-CAS silence, account
isolation, reconnect/readback, renewal/sign-out closure, real Ping/Pong beyond
the HTTP budget, per-owner capacity release and shutdown with an open socket.
No host credentials, public endpoint or cloud delivery is used. The disposable
PostgreSQL owner invokes a separate durable-store phase through
`make backend-postgres-parity-test`; a WebSocket itself has no persisted history.

Captured cases do not establish all parser/handshake/status/body equivalence,
TLS proxy behavior, multi-replica fanout, durable synchronization or native/CLI
Rust readiness. Read [scope and limits](../../behavior/rust-realtime.md).

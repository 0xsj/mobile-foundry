# Rust runtime foundation

Established 2026-10-18. Rust implements a deliberately bounded subset of the
[HTTP runtime contract](../http/runtime-v1.md), following the Go profile's
`cmd` / `pkg` / `internal` ownership. The
[parity inventory](../../backend/rust/PARITY.md) records remaining differences.

The process validates its environment snapshot, selects safe logging, installs
signal and redacted panic handlers, binds a listener and transfers it to a
single-use server. Configuration is injected; reusable modules do not read the
environment. Unsupported module/provider selections fail startup.

The Health module's application query owns a dynamic readiness port. Process
composition combines serving state with selected delivery-worker/outbox probes.
Canonical GET/HEAD live and ready paths, fallback 404/405, public failure kinds,
request IDs and correlation admission follow the shared runtime fixture corpus.
Public failures strip private/internal information. HEAD retains representation
headers while removing every response body, including middleware failures.

Serving starts false, becomes true when the bound listener is served, and becomes
false before graceful drain. SIGINT/SIGTERM stops admission; existing requests
can finish without receiving process-signal cancellation. The server owns its
connection tasks and waits up to ten seconds. Expired drain aborts and joins
those tasks, closes connections and returns failure. Delivery workers are owned
separately by process composition. The [PostgreSQL extension](rust-postgres.md)
adds explicitly selected pools. A dropped
server future also clears readiness.

The explicit [memory Identity module](rust-identity.md) now adds owned blocking
password calculations. After HTTP drain, process composition fences/joins all
[delivery workers](rust-audit.md), then closes that crypto adapter, waiting for
admitted work, then closes all selected pools. It still has no generic module
lifecycle registry.

Only unwinding panics during buffered response assembly reach the problem
boundary. The process panic hook emits no payload or location. Cooperative
query deadlines are fifteen seconds and never imply remote rollback/retry.

The kernel supplies a swappable business wall clock and a canonical nonzero UUID
value/generation port. Clock time is separate from the runtime's monotonic timers.
Random ID allocation returns OS entropy failure; the boundary maps it to generic
500 with no invented request ID and never runs the endpoint. Server composition
can select another generator while malformed IDs remain unconstructible.

Transport admission bounds the full JSON body to 16 KiB under the request deadline,
requires one application/json Content-Type, rejects non-objects/trailing values
and observes ignored fields during typed decoding. Size/media/precondition-required
failures own 413/415/428 status overrides. Strong If-Match values are canonical
quoted integers in 1..9007199254740991; optional Idempotency-Key follows the token
grammar. Response encoding completes before a response is returned. Health uses
that same encoder and the boundary retains HEAD representation headers.

Admission is verified with a shared optional-string/nested-bool DTO. It does not
establish every Go DTO rule: duplicate intermediate values, case folding, null,
number and invalid-Unicode handling need endpoint-owned compatibility cases.
Parsing a key or version does not supply receipts, replay or atomic CAS.

Shared fixtures establish canonical runtime HTTP, individual public failure
projection, identifiers and bounded JSON/header admission. Rust listener tests
additionally exercise failed probes and active/forced drain. Identity has its own
[separate acceptance](rust-identity.md), as does [Account/capability composition](rust-account.md).
The runtime corpus alone does not
establish the full Go router/parser/network budget, other business modules,
inter-domain communication, database durability, native integration, deployment
or independent CLI starter readiness.

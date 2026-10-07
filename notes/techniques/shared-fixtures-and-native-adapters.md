# Shared fixtures and native adapters

Portable fixtures establish behavior parity; native adapter tests establish resource and SDK behavior.

## Origin

2026-10-08 HTTP slice. [Response fixtures](../../contracts/fixtures/http/responses.json), [Path fixtures](../../contracts/fixtures/http/paths.json), and [Retry fixtures](../../contracts/fixtures/http/retry-after.json) are consumed by both
native host suites. Native sockets and callbacks need additional platform tests.

## What and why

Keep expected outcomes outside either implementation. A shared case supplies
wire status, headers, body, and independent expected kind or domain admission.
Each language reads that oracle rather than deriving expectations from its own
status mapper. An injected clock makes date-based retry timing repeatable.

Then check the adapters separately: URLSession uses a controlled URLProtocol for
success, offline failure, and cancellation; a loopback TCP server checks
interrupted bodies and redirect refusal. OkHttp uses MockWebServer to exercise
real exchanges, delayed body reads, cancellation, and redirect refusal.

## Example and observation

A synthetic URLProtocol that delivered a response and then failed did not retain
the response headers through Foundation's callback path in this environment.
Adding delay did not change that observation. A real loopback response advertised
99 bytes and closed after one byte; the executed test retained HTTP 503 and its
request ID. The final test uses that real exchange to establish the body-error
claim. Synthetic callback order alone was insufficient evidence.

## Gotchas

A green JSON fixture suite does not establish socket cancellation or UI lifetime.
A successful APK or simulator build does not establish a rendered screen.
Device smoke checks do not establish accessibility, background scheduling, or
performance on physical devices. Record each level separately.

## Used in

Cross-platform foundations and backend conformance scenarios. Test fixtures are
checkout inputs; runtime libraries and apps do not load repository files.

## Related

[Service boundaries](../patterns/transport-service-and-screen.md) and [Deadline ownership](../concepts/deadlines-and-owned-cancellation.md).

# Bounded private HTTP metrics

Claim: fixed HTTP method/status counters provide useful runtime evidence without
turning user-controlled paths or identity into unbounded metric labels.

Origin: portable operations slice, 2026-10-17. Read
[collector/wrapper](../../../../pkg/telemetry/http.go) and
[tests](../../../../pkg/telemetry/http_test.go).

The wrapper records active handlers, count and summed/count duration for bounded
method/status classes. It preserves Hijack and Unwrap so WebSocket upgrade and
ResponseController remain available. The separate listener is off unless configured;
the production Compose profile exposes it only on the private network.

Conceptual: GET to any account contributes to the same method/status bucket.
Account IDs, URLs, mail addresses and credential values never become labels.

Tests observe response capture, upgrade compatibility and absent secret labels;
container acceptance reads the actual metrics endpoint. These are HTTP counters,
not database saturation, distributed tracing, alert policy or open-socket gauges.
Next: add a small bounded operational observation only when a concrete incident
or service budget needs it. See [operations](../../../../../../contracts/behavior/backend-operations.md).

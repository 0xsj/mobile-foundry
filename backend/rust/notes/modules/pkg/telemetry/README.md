# Fixed-label HTTP observations

Claim: a finite label vocabulary and a scoped observation guard bound metric
cardinality without retaining private request values.

Origin/evidence: 2026-10-18 Rust 1.86, Go's telemetry reference and local listener/
process tests. The [Prometheus text format](https://prometheus.io/docs/instrumenting/exposition_formats/)
defines the exported counter/gauge/summary syntax.

Read [Recorder, Observation and middleware](../../../../pkg/telemetry/src/lib.rs).
An eight-by-six counter array indexes fixed methods/status classes. begin increments
active and retains only the category/start time. status selects the final response
class. Drop decrements active and updates count/duration even if a handler future
is canceled; an unsettled dropped observation defaults to 5xx. There are no URI,
header, actor or credential fields to accidentally serialize as labels.

Example (Conceptual): thousands of different custom method tokens produce one
OTHER bucket; account-specific paths produce no new series. A currently held guard
raises active by one and disappears when ownership ends. Duration is elapsed
monotonic time, independent of business clock injection.

[The server](../../../../internal/server/src/lib.rs) installs the middleware only
with selected private metrics, outside the safe HTTP boundary. The private router
does not install observation on itself. Rust's 101 response completes HTTP admission;
the separately owned socket is not included in handler active/duration metrics.
Go's handler remains active while hijacked, an explicit comparison limit.

Verification: embedded tests check cardinality, active/drop, fixed classes,
sensitive path/query/header absence and route/method admission. Actual listener
and [shared process checks](../../../../../../scripts/operations_parity.py) prove
the monitor is separate and disappears on shutdown. Metrics do not prove business
commit, provider delivery or a latency budget.
Related: [server ownership](../../internal/server/README.md).
Next: which additional fixed business counters merit independent owner ports?

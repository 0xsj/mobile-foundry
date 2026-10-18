# Health application and transport

Claim: readiness belongs to an application-owned query port while HTTP owns its
wire representation and request deadline.

Origin/evidence: 2026-10-18 probe failure test and live/ready process corpus.
Read [composition](../../../../internal/health/src/lib.rs),
[query/port](../../../../internal/health/src/app/query.rs),
[v1 adapter](../../../../internal/health/src/transport/http/v1.rs), and
[readiness test](../../../../internal/health/tests/readiness.rs).

CheckReady asks an injected Readiness dependency. Failure becomes the consumer's
health.not_ready condition with the dependency as its private source. The HTTP
adapter applies the request deadline, renders a safe 503 or returns status ok.
Liveness does not ask the dependency. Composition currently selects the server's
serving flag; future module probes must be composed explicitly.
Health now uses the shared response encoder rather than an independent literal
body/length pair. Module composition accepts a BoundaryState for swappable IDs;
the convenience builder selects the default OS generator.

For example, the injected failing probe produces 503 readiness while liveness
continues to return 200. The test confirms its private dependency diagnostic never
enters the body. Twenty-seven shared process cases cover canonical Health/fallback
methods, metadata, HEAD and public response behavior in the default ready state.

Gotchas: exact path matching currently differs from Go ServeMux normalization;
selected worker/outbox probes now extend serving state; there is no database
probe or general module assembly registry. Health needs no invented
domain model. Related: [async ports](../../../language/results-and-owned-async-ports.md),
[server](../server/README.md). Next: how will selected modules contribute probes
without making Health import their concrete stores?

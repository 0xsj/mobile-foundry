# HTTP admission and projection

HTTP owns wire admission and response commitment, while shared errors own
classification and application handlers own business meaning.

Origin: 2026-10-12 source inspection and executed tests. Read
[helpers](../../../../pkg/httpx/httpx.go),
[tests](../../../../pkg/httpx/httpx_test.go), and the
[wire contract](../../../../../../contracts/http/runtime-v1.md).

Methods selects an endpoint and turns its returned error into a problem response.
The outer Boundary supplies request metadata and cooperative deadline, tracks
response commitment, reports panics and logs completion. Fresh request IDs are
owned by the server; admitted correlation IDs may come from a caller. Default
logs intentionally use safe metadata rather than arbitrary errors or bodies.

JSON encoding occurs before writing headers, so encoding failure can still
become a generic 500. After commitment, failures are logged without attempting
a second response. HEAD computes the corresponding GET headers but emits no body.
Decoder admission limits bytes, content type, object shape, unknown fields and
trailing values; business validation still belongs to domain constructors.
Duplicate JSON members retain decoder last-key-wins semantics.

`make test` covers JSON rejection, internal privacy, panic isolation, metadata,
HEAD/method errors, version/key parsers, denied credentials and encoding failure.
The parsers alone provide no replay or version concurrency. There is no auth
adapter beyond a denying resolver and no TLS/CORS/streaming policy.

Related: [failure ownership](../errors/README.md),
[Health](../../internal/health/README.md), and
[toolchain](../../../substrate/go-runtime-toolchain.md). Next: which atomic store
contract would make an Idempotency-Key safe after an uncertain response?

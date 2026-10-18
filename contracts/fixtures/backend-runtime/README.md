# Backend runtime fixtures

Shared by Go and Rust. Run `make backend-parity-test` from the repository root.
See [Rust scope and gaps](../../../backend/rust/PARITY.md) before interpreting
this as overall backend parity.

`http.json` is the black-box canonical HTTP corpus. Each case owns a method,
path, optional ordered header pairs (preserving duplicates), expected status,
optional exact Allow/correlation header, and expected JSON body for non-HEAD
responses. Error request IDs are dynamic: the runner checks UUID v4 freshness
and injects the response header ID into the expected problem. All responses
must carry no-store/nosniff and the correct content type and body length. HEAD
must be empty with nonzero representation length. Caller IDs must be replaced.

`failures.json` supplies individual classified conditions to each language's
test-only projector: private diagnostics/causes are always supplied, optional
public text and fields come from the case, and the expected problem is literal.
All ten kinds plus absent public text are covered. The request ID is fixed only
for projection tests. No production API has fixture/error injection routes.
The [Go projector test](../../../backend/go/parity/runtime_test.go) is
repository-owned conformance work outside the CLI starter's selected source
closure; generated Go products keep their existing independent test fixtures.

`admission.json` contains 26 body/media cases with an optional string `name` and
optional nested `detail.enabled` boolean DTO. The optional `pad_to` extends the
UTF-8 wire body with spaces to test the inclusive 16384-byte limit without a huge
fixture file. Successful values include the DTO's absent/null fields explicitly;
failures compare status/code/detail. Media refusal precedes body-size reading.

`headers.json` contains 27 required strong-version and optional-key cases. Ordered
header pairs preserve repeats. Success is a version number or optional key;
failure is status/code/detail, including missing If-Match's distinct 428.
`ids.json` has 11 canonical UUID cases, including non-v4 values and malformed/
zero/uppercase refusals. It validates opaque IDs, not authentication or ownership.
These run in [Go admission tests](../../../backend/go/parity/admission_test.go),
[Rust admission tests](../../../backend/rust/pkg/httpx/tests/admission.rs) and
[Rust identifier tests](../../../backend/rust/pkg/kernel/tests/ids.rs).
Fault tests additionally exercise ID-generation refusal, stalled/broken body
reads and response encoding failure without introducing production test routes.

The process runner builds both real APIs with fresh application environments,
checks the same expected values against both, looks for sensitive fixture input
in logs and requires clean SIGTERM exit/listener closure. It owns temporary
output and terminates children in failure paths. Go uses memory storage with
denying authentication; no database/provider settings are inherited.

Excluded: encoded/cleaned paths and redirects, parser-level malformed HTTP,
full Go DTO/MIME edge semantics, joined error identity, full network budgets, auth/business routes, database,
streaming, deployment and generated product acceptance. Failed probes and active
drain currently have language-specific tests rather than shared process cases.

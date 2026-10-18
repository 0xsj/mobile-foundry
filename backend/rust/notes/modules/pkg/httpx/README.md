# HTTP boundary and problems

Claim: transport-owned metadata and public projection keep business failures
separate from arbitrary request content and private diagnostics.

Origin/evidence: 2026-10-18 Rust tests and shared real-process corpus. Read
[boundary/deadline](../../../../pkg/httpx/src/lib.rs),
[problem projection](../../../../pkg/httpx/src/problem.rs),
[safe logger](../../../../pkg/httpx/src/logger.rs), then
[boundary tests](../../../../pkg/httpx/tests/boundary.rs),
[admission helpers](../../../../pkg/httpx/src/admission.rs) and
[admission tests](../../../../pkg/httpx/tests/admission.rs).

The boundary asks its injected Generator for a fresh UUID, admits at most one bounded correlation token,
inserts owned request context and runs the next handler under unwind capture.
Problems are fully encoded before leaving the handler. Kind maps to HTTP status;
HTTP-owned admission selects its 405/413/415/428 overrides. Response extensions carry
the public classification for safe logging. The boundary adds security metadata
and strips HEAD bytes while retaining the representation length.

The logger exposes named events rather than arbitrary request/error objects.
It logs request ID, method, status, elapsed time and safe classification. A panic
loses its payload at projection; process composition must also replace the
default panic hook. The fifteen-second context/deadline helper stops cooperative
queries without retrying. Its virtual-time test does not wait fifteen real seconds.

The JSON reader checks media before collecting at most 16 KiB under that same
deadline. It normalizes a complete object and observes ignored typed fields.
Version and key readers preserve repeated-header refusal and distinguish missing
required preconditions. JSON encoding completes before creating a response;
Health uses this common helper. A private AdmissionError owns status overrides;
domain code strings do not select them.

Identity now consumes the shared bounded byte reader with an endpoint-owned
ordered string decoder, rather than silently inheriting generic Value
normalization. Its credential routes preserve Go duplicate/type/null/case rules
for the shared corpus. PeerAddress is selected only from accepted sockets by
the server. It is not sourced from an HTTP forwarding header.

The follow-up executes 26 JSON and 27 header fixtures in both languages. Rust's
fault tests separate a stalled stream, broken stream, serialization failure and
ID-generation refusal. The last must not run the handler, fabricate a request ID
or leak a diagnostic; GET and HEAD are checked. The corpus does not settle every
Go DTO rule, as [decoder compatibility](../../../substrate/json-admission-compatibility.md)
explains.

Example: invalid duplicate correlation headers become a 400 problem with a new
request ID, no echoed correlation and no HEAD body. The shared corpus exercises
this against both actual APIs; the Rust panic test verifies response/log privacy.

Gotchas: arbitrary public code/text remains the condition author's responsibility.
Native parser failures occur before this boundary; streaming, post-commit failure,
full DTO equivalence and network budgets are still gaps. Parsed versions/keys
do not establish atomic CAS or receipts. Used by
[Health](../../internal/health/README.md). Related:
[toolchain](../../../substrate/cargo-and-http-runtime.md). Next: which Identity
DTO and capability cases must be fixed before native clients can select Rust?

# Shared runtime fixtures

Claim: process-level conformance and test-only condition projection exercise
different boundaries and should share expected values without exposing debug APIs.

Origin/evidence: 2026-10-18 execution of
[the runner](../../../../scripts/backend_parity.py): 27 HTTP cases against each
compiled API and 11 public-failure cases in each implementation's tests passed.
The follow-up adds 26 JSON/media, 27 version/key and 11 identifier cases per
implementation, plus safe ID-generation and Rust body/encoding fault checks.
Both owned processes exited successfully after SIGTERM and closed their listeners.

Account follow-up: the separate [Account runner](../../../../scripts/account_parity.py)
reuses process ownership/HTTP helpers while selecting real Identity authentication.
It executes seven shared name cases and forty HTTP fixtures, then concurrent
bootstrap/CAS, owner paging and credential renewal/revocation flows. A separate
default-deny phase ensures co-located routes confer no authentication. Each phase
gets a fresh process and no host provider settings; expected outcomes remain
explicit rather than merely comparing one implementation with the other.

Read [the fixture format](../../../../contracts/fixtures/backend-runtime/README.md),
then the Go projector test and Rust boundary test linked there/from the source
walkthrough. HTTP cases use ordered header pairs to preserve duplicates. A new
request ID is checked for format, freshness, caller replacement and problem/header
consistency; it is not compared literally between implementations. HEAD is checked
as empty bytes with a representation length.

The runner supplies a fresh allowlisted application environment and uses temporary
loopback processes. It waits for each child's bound-listener startup marker,
then probes readiness. It always terminates children on failure and removes its
temporary output. Diagnostic conditions stay inside tests, so the production
APIs gain no failure injection endpoint. Private fixture input must not appear in
captured runtime logs.

Gotchas: ephemeral port reservation is released before launch; a bind race must
fail the child rather than fall back to a different server. Startup markers plus
a live child guard ownership. The runner is local Unix acceptance, not a hostile
multi-user harness. It does not test TLS/proxy routing, malformed parser input,
database state, active drain across both processes or cloud delivery. Rust's
separate active/forced-drain tests cover its lifecycle implementation.

Used in [runtime parity](../../PARITY.md) and the root Make target. Related:
[behavioral parity](../concepts/behavioral-parity.md). Next: what fixture metadata
will keep account ownership, receipts and uncertain acknowledgements explicit?

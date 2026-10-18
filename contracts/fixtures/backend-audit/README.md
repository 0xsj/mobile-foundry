# Event and Audit conformance

Observed 2026-10-18. [Events](events.json) has 33 Account/core Identity admission
cases. Each case chooses source, expected valid result and a complete envelope.
Optional data_wire replaces data with raw JSON to retain duplicate member order.
Both languages execute actual receiving adapters and Record/store admission;
successful cases must produce one scoped evidence entry. They are expected-value
checks, not just comparison between implementations.

[Access responses](access-responses.json) has seven raw capability replies.
Only an explicit boolean establishes allowed/denied. Missing/null/wrong-type or
unknown fields must remain failures. Each language executes its consumer adapter.

[HTTP](http.json) has 24 expected status/problem/header cases. Paths replace
`{account}` with a phase-owned account. Method defaults GET; auth defaults owner,
false omits credentials, and "other" selects another authenticated principal.
Additional headers retain ordered pairs. HEAD checks empty bytes. These cases
include Audit's Atoi-compatible limits, which differ from Account's policy.

Run `make backend-audit-parity-test`. The [runner](../../../scripts/audit_parity.py)
also exercises real event delivery/causal metadata, bootstrap without duplicate
facts, account/personal cursor isolation and Identity renewal/revocation evidence.
Each phase owns fresh API processes/ports and checks private logs, SIGTERM and
listener closure. No fault injection endpoints enter the public API. Separate
Rust composition tests inject lost acknowledgments and assert one original
Audit entry. Read [the bounded scope](../../behavior/rust-audit.md); this does not
establish database or broker recovery, arbitrary parser equivalence or CLI readiness.

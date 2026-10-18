# Installation registry conformance corpus

Ten proof cases check canonical unpadded i1 encoding and fixed namespaced hashes.
Twenty route/token cases cover APNs/FCM policy, application/token byte limits and
fixed NUL-separated target digests. Twenty-one revocation cases cover closed v1/v2
reasons, producer/scope/actor/version agreement, ordered duplicate/null/type rules
and fixed source fingerprints. Forty-two HTTP cases cover proof/body/header/query
admission, preconditions, sanitized views and ordered registration DTOs.

[Go checks](../../../backend/go/parity/installations_test.go) and
[Rust checks](../../../backend/rust/internal/notifications/tests/installation_admission.rs)
consume the same expected results; values are not merely compared to one another.
[The runner](../../../scripts/installations_parity.py) exercises actual APIs,
including token rotation, explicit principal rebinding, renewal, unregister and
all four session revocation reasons. Private local SMTP supplies reset codes.
The disposable PostgreSQL runner additionally exercises transaction faults,
killed pending delivery and Go↔Rust retained proof/target/marker handoff.

No actual provider-issued token, external push or native Rust starter is implied.
Read [shared behavior](../../behavior/installations.md),
[Rust scope](../../behavior/rust-installations.md) and the
[parity inventory](../../../backend/rust/PARITY.md) for remaining wire/runtime gaps.

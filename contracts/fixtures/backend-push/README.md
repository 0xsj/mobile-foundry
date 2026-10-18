# Push foundation conformance corpus

Twenty-one attempts assert gateway call count, disposition, error kind, conditional
retirement and retained registry version/enablement. They include mutation during
eligibility/send, uncertainty and known rejection with failed cleanup. Twenty
requests assert strict capability fields, caller policy, ordered strings/nulls
and malformed input. Fourteen response cases require an explicit boolean with
the captured pointer-bool duplicate/null rules. Six session cases distinguish
short access expiry from absolute expiry, creation time, ownership and absence.

[Go checks](../../../backend/go/parity/push_test.go) and
[Rust dispatch conformance](../../../backend/rust/internal/notifications/tests/support/push.rs),
[Identity queries](../../../backend/rust/internal/identity/tests/eligibility.rs)
and [consumer replies](../../../backend/rust/internal/notifications/tests/push_access.rs)
consume the same expected values. They do not merely compare implementations.
[The runner](../../../scripts/push_parity.py) also invokes Go's existing dispatch
tests, real Rust inter-module composition and default-route refusal in compiled
APIs. [PostgreSQL acceptance](../../../scripts/postgres_parity.py) runs target/
session conformance, rollback, corrupt-state refusal and separate database owners.

Gateways are injected in tests. No network send, real provider token/key, persisted
attempt, retry receipt, cloud delivery or Rust starter acceptance follows. Read
[shared behavior](../../behavior/push-dispatch.md) and
[Rust scope/evidence](../../behavior/rust-push-dispatch.md).

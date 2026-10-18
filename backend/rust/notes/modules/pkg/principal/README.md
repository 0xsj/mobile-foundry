# Shared credential evidence at HTTP edges

Claim: sharing a narrow credential resolver does not require sharing Identity
state or conflating identity with account authorization.

Origin/evidence: 2026-10-18 extraction from the Account adapter when Audit became
its second HTTP consumer; existing credential tests and both modules' Go/Rust
process acceptance. Read [types/port](../../../../pkg/principal/src/lib.rs), then
[credential adapter](../../../../pkg/principal/src/capabilities.rs).

Principal holds admitted principal/session IDs and absolute expiry. Resolver
returns that evidence or Failure. The private adapter invokes credential v2 as
the process-selected api client and validates returned IDs/time. Deny remains
the default. Account retains compatibility exports, so its existing
[adapter tests](../../../../internal/account/tests/credential_adapter.rs) now
exercise this shared implementation. No Identity crate enters this package.

Raw credentials remain at the HTTP/private invocation edge. Business Audit
queries instead receive a trusted principal ID and ask their own AccountAccess
port for account.read. The process binds that client to audit, which is distinct
from the api credential caller. A failed capability cannot become anonymous
success or trigger fallback to another authority.

Example: Account HTTP and Audit HTTP resolve the same session evidence; Audit
then separately authorizes a requested workspace. Supplying a principal_id query
field cannot change who that session represents. Both routes refuse revoked
credentials in actual process tests.

Gotchas: the shared type is evidence, not an authenticator, selected workspace,
membership snapshot or global context field. There is still no remote service
identity adapter. Run `make backend-identity-parity-test backend-account-parity-test
backend-audit-parity-test` for selected process behavior. Related:
[capability boundary](../capability/README.md),
[Account ownership](../../internal/account/README.md). Next: which additional
evidence would require a new credential contract rather than a consumer guess?

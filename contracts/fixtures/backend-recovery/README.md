# Recovery conformance corpus

Twenty email policy cases preserve the existing Go canonicalization/admission,
including ASCII local/DNS bounds and simple dotted-I lowercase. Fifteen event
cases cover principal lifecycle revisions and the closed v1/v2 revocation reason
sets. Twenty-one HTTP cases cover authenticated contact/change admission, public
reset admission, DTO duplicates/null, query/key refusal and absent code handling.

`scripts/recovery_parity.py` runs Go/Rust policy, event and real HTTP checks,
plus private loopback SMTP capture for actual verification/reset/change flows.
Rust also tests TLS/STARTTLS using ephemeral test certificates; those tests need
the local OpenSSL executable. `scripts/postgres_parity.py` adds owned transaction
faults, principal-lock races and actual cross-language challenge restart recovery.
No private host configuration or real recipient/provider is used by these runners.

See [shared behavior](../../behavior/identity-recovery.md) and
[Rust scope/evidence](../../behavior/rust-recovery.md). Malformed Unicode repair,
uncaptured network/parser differences and live external delivery remain outside
this corpus. These checks do not register a native or CLI Rust profile.

# Backend Identity fixtures

Run `make backend-identity-parity-test` from the repository root. This checks
core memory Identity, not the complete Go backend or generated/native profiles.

- `policy.json`: 27 login/name/password cases. Canonical outputs are literal;
  password bytes are never normalized. Includes Unicode case/trim/count and byte limits.
- `security.json`: a published test password/PHC vector generated with Go's
  existing Argon2 parameters and fixed test salt; zero-byte opaque-token digest
  vectors independently computed with SHA-256. They are test data, never deployment
  credentials. Each actual adapter verifies the same values.
- `http.json`: 17 endpoint admission cases. They run against fresh actual Go/Rust
  processes with explicit identity/memory configuration. The custom string DTO
  cases cover case folding, duplicate members, null retention and early type errors.

[Go tests](../../../backend/go/parity/identity_test.go) live outside the CLI Go
source closure. [Rust checks](../../../backend/rust/internal/identity/tests) cover
policy, security and application failure/race behavior.
[The process runner](../../../scripts/identity_parity.py) owns its API processes
and clears inherited application/provider configuration. Separate fresh phases
exercise session flows, wire cases and the burst gate so their budgets do not
accidentally depend on one another. Runtime helpers supply process cleanup.

Core flows verify profile-only registration, canonical sign-in, safe wrong/unknown
credentials, live/HEAD profile reads, wrong-purpose tokens, rotation, old-access
refusal, consumed-refresh revocation, idempotent known-family sign-out and session
isolation. Gate checks vary X-Forwarded-For while requiring the same actual socket
source budget. Every phase checks private-input/issued-token absence from logs
and clean SIGTERM/listener closure.

Random IDs/credentials and issued timestamps are checked by invariant rather than
equated between processes. No real user's credentials, provider setup, database,
mail service or native device is involved. Malformed Unicode repair, full routing,
distributed limits, recovery/Audit/persistence and generated products remain outside
this corpus. See [Rust Identity scope](../../behavior/rust-identity.md).

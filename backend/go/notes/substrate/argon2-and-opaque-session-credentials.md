# Argon2 and opaque session credentials

Password verification and random bearer verification need different storage
mechanics, with bounded resource use and explicit failure handling.

Origin: 2026-10-13, Go 1.27.1, `golang.org/x/crypto` v0.57.0. Read
[adapter](../../internal/identity/infra/security/security.go),
[checks](../../internal/identity/infra/security/security_test.go) and
[dependency pins](../../go.mod).

Primary references: [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[Go Argon2](https://pkg.go.dev/golang.org/x/crypto/argon2),
[OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
[RFC 9700 refresh rotation](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2).
The RFC supplies relevant rotation reasoning; this API does not implement OAuth.

## What and why

Passwords have human-selected entropy and require a deliberately expensive
verification function. The adapter uses Argon2id at OWASP's stated baseline of
19 MiB/two iterations/one lane, random 16-byte salt and 32-byte output. It admits
only the supported PHC encoding/parameters before allocating KDF work. Stored
parameters cannot demand arbitrary memory or CPU, and corruption stays internal.
That fixed encoding is an adapter policy; future parameter upgrades need an
explicit multi-version verification/rehash design.

A buffered channel bounds concurrent KDF work to two calls. It is a resource
gate, not password policy or per-user authorization. Cancellation is checked
around the computation because Argon2's IDKey has no context parameter. Unknown
logins use a dummy hash to avoid skipping the expensive path; this does not prove
constant-time end-to-end HTTP behavior or eliminate all enumeration signals.

Excerpt of the bounded computation:

```go
select {
case p.work <- struct{}{}:
default:
    return nil, app.ErrBusy
}
defer func() { <-p.work }()
```

Access/refresh secrets instead come from a cryptographically secure random
source with 256 bits of entropy. Purpose prefixes and strict base64url admission
prevent swapping the two token roles. SHA-256 indexes the opaque secret in the
store; it is not used to hash passwords. Raw secrets never persist with their
digests. Lookup admits current session state, enabling immediate subsequent
resolution failure after revocation without a JWT signing-key lifecycle.

## Observed execution and limits

Race-enabled security tests observe distinct salts, valid/wrong-password answers,
oversized PHC parameter refusal, cancellation/work budgets, token uniqueness,
purpose/encoding refusal and source admission limits. Store tests observe strict
rotation/reuse across memory/PostgreSQL. Compiled PostgreSQL API probes scan
owned table snapshots and logs for every issued raw token/password; none appear.

The local edge gate is bounded per IP/endpoint and resets with the process. It is
not distributed protection, a credential-stuffing service or a throughput/power
benchmark. No password breach screening, recovery, MFA, passkeys, TLS deployment
or key/secret-rotation service is supplied. Native credential-vault behavior is
unchanged; a real Go SessionAuthority adapter is future work.

Related: [Identity](../modules/internal/identity/README.md),
[scope boundaries](../patterns/principal-session-and-account-boundaries.md) and
[pgx transaction uncertainty](pgx-and-postgresql.md).
Next: measure KDF cost on deployment hardware before changing admission budgets.

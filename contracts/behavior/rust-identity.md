# Rust memory Identity core

Established 2026-10-18. Rust implements the core memory-backed subset of
[Identity/session behavior](identity-sessions.md) and the
[Identity HTTP v1 contract](../http/identity-sessions-v1.md). Select explicitly with
`AUTH_MODE=identity`; default deny mode registers no Identity routes.

Implemented endpoints are register, sign-in, renew, sign-out and me. Registration
returns a principal profile and never issues a session or creates an account.
Grants preserve the existing protocol authority ID `go.identity.v1`; this names
the compatible credential contract, not a shared token database. A grant works
only in the process/store that issued it. Profile, UTC microsecond expiry,
credential prefixes, version and public refusal shapes follow the existing v1
contract. No idempotent mutation receipts or automatic replay are supplied.

The module owns `domain`, `app/command`, `app/query`, application ports,
`infra/memory`, `infra/security` and `transport/http/v1`. Domain code imports no
HTTP/SQL/runtime types. Store, Passwords, Tokens and AttemptGate are
consumer-owned ports; module composition selects adapters. Scope/Metadata are
mechanical kernel context shared with Account/capabilities; HTTP adapts the
request context and capability invocation adds its own budget check.
Business time and IDs use the existing kernel ports.

Login/display-name/password policy follows the shared Go policy corpus. Password
and token wrappers redact Debug and erase their owned String storage on drop;
this is not a claim that every runtime/body/stack copy is erased. Crypto uses
Argon2id v19, 19456 KiB, two iterations, one lane, 16-byte random salts and
32-byte output. Stored parameters/encodings are strictly admitted; corruption
is internal rather than a wrong-password result. Unknown valid logins perform
dummy verification. Access/refresh secrets are independent 256-bit random bytes;
only purpose-checked SHA-256 digests enter session state.

The password adapter admits at most two calculations, keeping permits inside
the blocking jobs. Canceling an HTTP caller does not interrupt a running KDF or
release its capacity early. After HTTP drain, module close fences new work and
waits for those owned calculations. The ten-second HTTP drain is not a total
KDF/process shutdown deadline. The local socket-IP/endpoint gate has burst 12,
one-per-second replenishment and at most 1000 keys; forwarded headers confer no
admission identity. This is not distributed abuse protection.

Memory Store commits principal/session changes, retained domain facts and mapped
integration outbox envelopes together
under one lock, after generating/checking fact IDs and checking the caller scope.
Capacities match the current Go memory baseline. Required-fact failure writes no
partial state. Rotation keeps the absolute expiry and predecessor refresh marker.
Reusing a consumed refresh commits revocation and a fact, returning an explicit
Reused outcome; the command then returns 401. That refusal is a committed
security result, not rollback. Independent sessions stay independent; repeated
known-family sign-out adds no second fact.

Credential HTTP decoding preserves string-field case folding, ordered duplicates,
null as a no-op and refusal of an earlier wrong-type value. It does not use the
generic Value normalization path. Unknown fields, non-objects, trailing data,
queries and unsupported Idempotency-Key are refused. Malformed Unicode repair
and full MIME/router/network-budget behavior remain documented parity gaps.

Evidence includes 27 shared policy cases, shared PHC/token vectors, 17 shared
live wire cases, real-process session flows and attempt limiting against Go/Rust.
Rust tests also cover registration races, failed-fact atomicity, renewal races,
restoration/time bounds, dummy verification and canceled-caller KDF ownership.
Run `make backend-identity-parity-test` and `make rust-check rust-test`.

Identity now exposes credential v1/v2 through a versioned capability transport.
The first [Account consumer](rust-account.md) uses v2 trusted session evidence;
v1 retains its principal-only shape. No store is shared across the boundary.

Core lifecycle envelopes now reach [independent memory Audit](rust-audit.md).
PostgreSQL now has [separate durable acceptance](rust-postgres.md).
Email/reset/change now have [separate recovery acceptance](rust-recovery.md).
Not implemented: selected maintenance, passkeys/OIDC, native connected acceptance
or CLI extraction. Retained
memory facts are not durable evidence or Audit receipts. Selected worker/outbox
probes now extend serving readiness; a generic lifecycle registry remains work.
Read [the current parity inventory](../../backend/rust/PARITY.md) before selecting
Rust as a replacement backend.

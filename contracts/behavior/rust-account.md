# Rust memory Account and credential capability

Established 2026-10-18. This is the implemented Rust subset of
[Account scope](account-bootstrap.md), [Account HTTP](../http/account-audit-v1.md)
and [module communication](module-boundaries.md). Go remains the reference.

Account owns its aggregate, personal mapping and application Store/Directory
ports. The HTTP edge uses the narrow shared Principal resolver. Domain code
imports no HTTP, runtime or Identity types.
The Account crate has no Identity dependency. Its capability adapter invokes
`identity.credential.resolve.v2`; process composition selects that adapter.
Credentials are verified by Identity's owned token/session ports. Trusted
principal/session IDs and absolute session expiry come from the capability,
never request body fields. There is no anonymous success or fallback on failure.

The in-process registry admits versioned names and explicit caller allowlists,
refuses duplicate registrations, checks required providers before serving and
consumes its mutable builder into an immutable registry. Requests/responses are
bounded JSON objects, at most 16 KiB, under the parent scope and a cooperative
two-second invocation budget. Typed decoders reject unknown fields and trailing
values. Provider failures cross public projection; private causes/diagnostics
are discarded. Invocation never retries or detaches a provider task. It is a
swappable Invoker port, not a remote transport or a business service locator.

Identity registers both credential versions for caller `api`. v1 returns exactly
`principal_id`; v2 adds `session_id` and absolute `session_expires_at`. Current
access expiry, rotation and revocation are checked before either succeeds.
The consumer validates response IDs/time and treats malformed evidence as an
internal failure. Scope/Metadata are mechanical kernel context, not authority.

The implemented Account routes are POST create, POST personal bootstrap,
GET/HEAD owner listing, GET/HEAD selection and PATCH rename. Default deny mode
mounts these routes but refuses credentials. Explicit `AUTH_MODE=identity`
composes the real memory credential capability. Registration still creates no
account; there is no principal-global selected-account state.

Names trim surrounding whitespace, admit 1..80 Unicode scalar values and refuse
controls. Domain restoration refuses noncanonical names, invalid version/time
and backward time; canonical typed IDs cannot contain malformed values. Times
are UTC microseconds, versions 1..9007199254740991. Responses retain Location on
create/bootstrap and strong ETags. Rename requires the observed strong If-Match
and rechecks that version under the store lock. Stale versions fail with 409.
Missing preconditions retain 428, separate from malformed 400 conditions.

Bootstrap atomically commits personal mapping, account and required local fact.
Concurrent callers have one creation winner; repeats return the current mapped
account, including later renames, without changing its name/version/facts.
Every request still validates the proposed name and allocates candidate/evidence
IDs before store admission. Ordinary create never adopts or changes that mapping.
Create/rename commits account and required fact together. Capacity, fact-ID or
scope failure before commit changes neither. Memory caps are 1000 accounts and
10000 retained facts. Each now has a matching envelope committed in the same
lock; [independent delivery/Audit](rust-audit.md) owns the receiving guarantee.

Lists filter by owner before returning up to the requested limit, ascending by
canonical ID. The default is 20, range 1..100. A versioned unpadded base64url
cursor binds owner and exclusive last ID; it grants no authority. Repeated,
unknown or malformed query fields/positions fail. Pages are live, not snapshots.
The Name DTO preserves ordered string duplicates, case-insensitive field names,
null no-op and refusal of earlier wrong types. List cursors own their decoder.
Other malformed Unicode/router/MIME edge cases remain in the parity inventory.

Writes have no command receipts and refuse Idempotency-Key. A lost response is
not rollback, so create/rename must not be retried implicitly. Bootstrap alone
has the documented repeat behavior. No cross-module call holds an Account lock.

Evidence: `make backend-account-parity-test` runs seven shared name cases,
40 HTTP fixtures and actual Go/Rust processes for concurrent bootstrap/CAS,
owner isolation, cursor paging, renewal/sign-out, default denial, private logs
and clean shutdown. Rust additionally tests restoration, atomic fact failures,
concurrent writes, malformed provider evidence, caller policy, failure projection
and owned deadline cancellation. Identity tests preserve both capability shapes.

AccountAccess and Audit now have [separate acceptance](rust-audit.md).
PostgreSQL now has [separate durable acceptance](rust-postgres.md).
Not implemented: invitations/membership roles, remote
capability routing, connected native Rust selection or CLI extraction. Follow
[the parity inventory](../../backend/rust/PARITY.md) before selecting a starter.

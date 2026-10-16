# Account and Audit workflow

Implemented 2026-10-13 as the first Go business slice. It exercises the
[module boundary policy](module-boundaries.md) with memory stores, a local
capability registry and independent event consumption. Extended with
[PostgreSQL stores](postgres-persistence.md) on the same date. Native integration,
remote routing, realtime and CLI extraction remain separate.

[Personal bootstrap and owned discovery](account-bootstrap.md) now add an explicit
one-per-principal personal mapping, paginated account listing and device-owned
selection through the existing guarded read.

The subsequent [Identity/session slice](identity-sessions.md) now supplies real
owned authentication under explicit selection. This document's credential fixture
section describes the separate development profile, not that authority.

## Account rules and writes

An account has a canonical nonzero UUID, owner principal ID, name, version and
UTC microsecond creation/update timestamps. Name admission trims surrounding
whitespace, requires valid UTF-8 and 1–80 Unicode code points, and excludes
remaining control characters. Restoration rejects noncanonical stored values.
[Name fixtures](../fixtures/account/names.json) exercise the domain policy.

Creation starts at version 1 and derives ownership from the trusted principal.
Read and rename require the owner; membership/invitations and role management
are future Account workflows. Rename increments the version even when the
admitted name is unchanged. Its expected version must match at write time;
a preliminary successful read does not bypass this compare-and-swap guard.
Ownership/creation time cannot be replaced through rename, and update time must
not move backwards. Versions stay within the positive JSON-safe integer range.

Application handlers receive their own Store, clock and ID ports. Infrastructure
maps the internal fact into an integration message. A failed mapping, admission,
capacity check, version check or pre-commit cancellation writes neither state
nor its outbox record. Successful state and required event append share one
store lock in this memory implementation. Cancellation or a lost response after
commit does not prove rollback; ordinary create/rename have no replay contract.
Personal bootstrap has the separately defined principal-keyed repeat behavior.
PostgreSQL uses one state/outbox transaction and repeats the version guard under
a row lock. Its durable delivery/sequence mechanics are defined separately.

## Identity fixture and HTTP

The default principal resolver denies authentication. To exercise this slice,
explicitly pair `DEV_AUTH_TOKEN` with `DEV_PRINCIPAL_ID`. The token is 8–128
characters without ASCII whitespace; the principal is a canonical nonzero UUID.
Startup rejects either setting alone, production use, or a non-loopback bind
when the fixture is selected. It is one development credential, with no sign-in,
renewal, revocation, expiry or persisted Identity authority. Composition selects
it behind the existing Resolver port; handlers receive the admitted principal.

The [HTTP specification](../http/account-audit-v1.md) defines:

- `POST /v1/accounts`: create an owner account.
- `POST /v1/accounts/bootstrap`: create or resolve the principal's personal account.
- `GET /v1/accounts`: list owned accounts with a scoped cursor.
- `GET /v1/accounts/{id}`: owner read; HEAD carries the same metadata without a body.
- `PATCH /v1/accounts/{id}`: rename using a required strong `If-Match` version.
- `GET /v1/accounts/{id}/audit`: owner-scoped evidence through Audit's access port.

Strict bounded JSON/projection comes from `httpx`. Ownership cannot be supplied
in a body. Unsupported `Idempotency-Key` is rejected before a write. Request and
optional correlation IDs are carried into evidence; credentials and account
names are excluded from event/Audit/log content. The account HTTP response
intentionally exposes the owner's account name.

## Synchronous access

Account provides [account.access.v1](../capabilities/account-access-v1.md).
Audit implements its own application AccountAccess port through a contract
client. It asks for `account.read` before loading evidence. The provider also
admits `account.rename`; the first allowed caller is `audit`.

Local calls serialize request/response objects, bound them to 16 KiB, validate
shape and apply a two-second cooperative budget bounded by the caller deadline.
Cancellation and late completion remain failures. Missing/malformed decisions,
unavailable providers and timeouts never become denial or absence. An admitted
negative answer denies the Audit read. Cross-module implementation imports
remain prohibited.

Assembly registers providers/subscriptions, verifies declared capability
requirements, freezes both registries and only then starts workers. Missing
providers fail startup regardless of builder order. Provider and consumer names
must belong to their registering module. Failed startup discards that runtime
and closes transferred resources in reverse order.

## Integration delivery and evidence

Account maps its selected internal facts to
[account.created.v1 and account.renamed.v1](../events/account-v1.md).
Messages contain stable event identity, producer, account/actor IDs, source
version/time, causal metadata and minimal data. They contain no account name.
The dispatcher delivers committed outbox records through an event Publisher.

The memory bus delivers detached envelope/payload values to every independently
registered matching consumer, even when another consumer fails. No consumer is
an unavailable-delivery outcome. Delivery is replayable, with no global order
promise. Audit's `audit.account` adapter admits the contract, checks payload and
envelope agreement and constructs its own Record command input. Account has no
Audit domain/application/store dependency.

Audit commits selected evidence and its consumer/event inbox identity together.
Concurrent duplicate delivery appends one entry; the same identity with different
evidence is rejected. Replays retain the first recording time. Evidence records
action, source version, account/actor/event IDs, occurrence/recording times and
request/correlation metadata, not arbitrary integration payloads.

The worker polls every 100 ms, selects at most 32 pending records and gives each
delivery a cooperative two-second budget. A timeout counts as a failed attempt;
worker cancellation leaves the record pending. A lost delivery acknowledgment
can cause replay. Failed deliveries are retried at most five times; exhausted
records remain retained and make Account readiness fail. These are bounded
development policies, with no administrative replay endpoint yet.

HTTP writes return after Account commit without waiting for Audit. A write may
succeed while Audit is temporarily unavailable. Audit reads can initially be
empty while delivery catches up. Shutdown drains HTTP before cancelling/joining
all workers before closing any module resources. The worker does not promise to
flush every pending record on exit. PostgreSQL claims one record per poll rather
than the memory batch, preserving a bounded delivery lease.

## Query and resource limits

Audit queries are ascending by its own append sequence, account-filtered and
keyset-paginated. Limits are 1–100, default 50; the opaque cursor binds account ID
to an exclusive position. A cursor is not authorization. The first owner check
is authoritative for this slice; no membership/revocation mutation exists yet.

Memory Account storage admits at most 1,000 accounts and 10,000 outbox records;
Audit admits 10,000 entries. Outbox records remain retained after delivery.
Capacity failure occurs before an atomic append. All records, inbox markers and
delivery state are lost on process exit. This proves wiring/replay/CAS semantics,
not durable recovery or production deployment.
Those capacity/restart limits describe memory only. PostgreSQL retains committed
records, attempts and inbox identities across API exits, with no pruning policy yet.

## Evidence

`make check` covers formatting, import boundaries, vet and build. `make test`
uses the race detector and real loopback HTTP: owner isolation, JSON/header
admission, version conflicts, Audit pagination, concurrent replay, dependency
failure and lost acknowledgment. `make smoke` compiles temporary binaries and
owns its loopback processes for startup/shutdown, opt-in credentials, HTTP/Audit,
private logs and expected memory loss after restart.
`make integration-test` verifies the PostgreSQL adapter and forced API crash/recovery
against a disposable database; see [persistence evidence](postgres-persistence.md).

Read the [construction map](../../docs/blueprints/account-audit.md) and
[Go learning order](../../backend/go/notes/README.md) for implementation paths.

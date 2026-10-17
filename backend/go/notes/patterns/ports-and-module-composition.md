# Ports and module composition

A provider remains replaceable when application code owns its required behavior
and composition is the only place that selects concrete implementations.

Origin: 2026-10-12 runtime slice; Bento source/convention inspection and current
Go import tests. This slice demonstrates readiness substitution, not business
store/provider interchangeability.

Follow-up, 2026-10-13: [Account](../modules/internal/account/README.md),
[Audit](../modules/internal/audit/README.md),
[capability invocation](../modules/pkg/capability/README.md) and
[event delivery](../modules/pkg/events/README.md) now implement the memory subset.
Race-enabled HTTP/replay/CAS checks and compiled-process smoke pass. A second
persistent provider is still needed to prove provider interchangeability and
crash recovery. The dated reference sections below explain how the seams were
chosen; their inspection limits describe that earlier investigation.

## What and why

Domain owns values and rules. A command or query owns one workflow and the ports
it requires. Infrastructure implements those ports; transport maps wire inputs
and results. Module wiring injects adapters into handlers. Process composition
selects modules and owns their startup/shutdown resources.

Splitting command and query directories does not require different databases,
event sourcing or separate processes. A named handler is useful when it makes
workflow ownership clear; a facade should delegate rather than absorb every rule.
The health example needs only a query because there is no mutation or aggregate.

## Bento conventions comparison — source inspection, 2026-10-17

Claim: the implemented backend follows Bento's main ownership boundaries, but
its newer modules do not yet follow every module-authoring and HTTP convention.

Origin/evidence: compared Bento's
[conventions](../../../../../bento/backend/CONVENTIONS.md) and
[module-authoring rules](../../../../../bento/backend/MODULE-AUTHORING.md)
with current Go source. This is a bounded source review, not a complete audit
of every operation or a fresh persistence/runtime acceptance run.

The matching pattern is visible in [Notes composition](../../internal/notes/module.go),
its [consumer-owned access port](../../internal/notes/app/ports.go) and
[capability client](../../internal/notes/infra/capabilities/account.go): application
code requires behavior, while the module root selects providers. The
[Audit receiver](../../internal/audit/infra/events/consumer.go) translates
integration messages into its own inputs without importing Account's implementation.
Go uses ordinary error returns. The
[PostgreSQL classifier](../../pkg/persistence/postgres/pool.go) preserves causes
and distinguishes recognized transient errors from other internal failures.

Concrete differences worth closing before copying these patterns into more modules:

- [Notes commands](../../internal/notes/app/command/write.go) combine create,
  update and delete in one `Write` handler, and
  [queries](../../internal/notes/app/query/read.go) combine several operations in
  `Read`. Bento prefers a named handler with `Handle` per operation and named
  inputs when several business fields are needed.
- [Notes domain values](../../internal/notes/domain/note.go) have public fields
  and JSON tags, and own not-found/version/command conflicts. Bento places
  use-case and port conditions in application errors, and keeps aggregate state
  private with detached snapshots. Notes currently contains scalar values, so
  the public fields do not themselves establish a shared mutable alias defect.
- [Account transport](../../internal/account/transport/http/v1/handler.go)
  rejects idempotency keys for create/rename. Bento supports optional keys and
  atomic replay/receipts. [Notes transport](../../internal/notes/transport/http/v1/handler.go)
  has atomic receipt semantics behind its store, but requires a UUID key and
  serves account-scoped receipt routes instead of Bento's shared receipt route.
  Its lists expose UUID `after`/`next_after` positions instead of opaque
  transport-owned cursors. These are public contract differences; changing them
  requires coordinated native clients/contracts, not just moving Go files.
- The shared PostgreSQL pool fixes connection count at eight and server statement/
  lock timeouts at five/two seconds. Bento specifies configurable pool sizing,
  thirty-minute idle retention and caller-context statement deadlines, with
  special bounded lock waits. Matching dependency direction does not establish
  matching operational defaults.

Example: a future `RenameNote.Handle(RenameNoteInput)` can retain the same atomic
store contract while separating its workflow and application errors from the
other operations. This is a proposed cleanup, not an implemented handler.

Verification: `make architecture-check` passed on 2026-10-17. It enforces import
direction and representative forbidden dependencies, including tests; it does
not check handler granularity, SQL semantics, cursor formats or error ownership.
Documentation links are checked separately with root `make notes-check`.
Next reading question: which differences are deliberate product contracts, and
which should be normalized before another module copies Notes?

Follow-up source trace, 2026-10-17: module communication already uses the two
implemented mechanisms described by Bento. Notes and Audit ask Account for a
current access decision through their own application ports and `account.access.v1`.
The [local invoker](../../pkg/capability/capability.go) admits JSON boundary
values, checks allowed callers, supplies a two-second context budget bounded by
the parent context, and performs no automatic retry. Cancellation is cooperative;
this context budget does not forcibly interrupt a provider ignoring its context.
Account and Identity instead communicate completed facts to Audit through
producer mapping, committed outbox records, delivery and Audit's receiving
adapters. The [Audit PostgreSQL store](../../internal/audit/infra/postgres/store.go)
commits evidence and its inbox marker together. Account does not invoke Audit
directly. Both mechanisms currently run in-process; remote capability routing,
broker delivery adapters and Broadcast/Realtime remain unimplemented. Versioned
contracts and ports provide extension points, not evidence that those transports
already work. Multi-module workflows need an application coordinator with
explicit intermediate states; they cannot assume one transaction across stores.

## Port and adapter flow

Conceptual:

```text
HTTP input → transport → query/command → application port
                                          ↑
                               selected adapter
```

For readiness, the adapter supplies process state and selected unit probes. A
different probe implementation can be injected without changing the query. A
future store needs a stronger contract: absence, scope, conflict, cancellation,
commit uncertainty and replay semantics must agree across implementations.

## Gotchas and use

Do not inject a provider SDK into a handler, access another module's store, or
interpret an arbitrary ID as authentication. Shared module assembly contains
resource mechanics; it must not become a catalog of business payloads/services.
The import checker prevents representative dependency violations, not runtime
access or SQL joins. Conformance tests arrive with actual provider adapters.

Used by Health and runtime assembly. Related:
[Health walkthrough](../modules/internal/health/README.md),
[module ownership](../modules/pkg/module/README.md), and
[language mechanics](../language/interfaces-errors-and-contexts.md).

## Synchronous capabilities and ownership — reference inspection, 2026-10-12

Claim: a consumer-owned application port can use a versioned capability without
coupling either module's domain/application to the other's implementation.

Origin/evidence: Bento's
[module inventory and service boundaries](../../../../../bento/backend/SERVICE-BOUNDARIES.md),
[Account contract clients](../../../../../bento/backend/go/internal/account/infra/capabilities/clients.go)
and [capability transport](../../../../../bento/backend/go/pkg/capability/transport.go).
Account's adapter implements its own identity/profile/access ports. The generic
router selects a local registered provider or a configured remote adapter.
Local invocation still serializes and admits boundary values, applies caller policy
and projects failures rather than sharing an implementation pointer.

Conceptual proposed example:

```text
Files command → Files-owned AccountAccess port → capability client
→ local registry → Account boundary adapter → Account access query
```

The command's port describes what Files needs. Account owns membership policy
and the published request/response contract. Switching invocation transport
does not move those rules. Authentication resolves the trusted principal at
the edge; an identifier alone is not proof of identity or access.

The inspected implementation uses a bounded deadline, required-provider
resolution and provider caller policies. Its Account client validates the
presence of a decision field: an absent/malformed response is a failure rather
than a false decision. This matters because dependency failure must not become
absence, denial, an unlimited plan or an enabled feature. A successful negative
answer and a failed request have different meanings.

Gotchas: a synchronous permission snapshot is not a cross-store transaction or
lock. Local calls need the same admission policy as remote calls. Transport
swappability alone does not implement service authentication, persistence or
safe retries. Mutations need owned operation identity/reconciliation rules.

Used in the new [capability catalog](../../CAPABILITIES.md) and
[module boundary policy](../../../../contracts/behavior/module-boundaries.md),
which distinguish immediate answers, committed facts and recoverable live hints.
Identity/Account/Audit form a small connected core; richer authorization,
realtime, delivery, files and commercial modules are independently selected.
The catalog retains their ownership rather than moving business services into
a shared dependency container.

Limit at the reference inspection: this is source inspection and an established blueprint,
not runtime verification of mobile capability routing. The catalog/policy and
reserved contract homes are documentation; no concrete schemas or adapters were
implemented. `make notes-check` checks documentation structure and links, not
these mechanics. Next: define one access contract and verify caller/deadline,
failure and response admission with the first business workflow.

## Integration events and Audit — reference inspection, 2026-10-12

Claim: versioned integration events let Audit interpret completed facts while
producer modules retain their own domain and application ownership.

Origin/evidence: inspection of Bento's
[event rules](../../../../../bento/backend/EVENT-RULES.md),
[delivery boundaries](../../../../../bento/backend/SERVICE-BOUNDARIES.md),
[Account producer](../../../../../bento/backend/go/internal/account/infra/events/producer.go)
and [Audit receiver](../../../../../bento/backend/go/internal/audit/infra/events/consumer.go).
This establishes reference design/source behavior; no event runtime was executed
or implemented in the mobile backend during this investigation.

Conceptual proposed flow:

```text
Account rename → internal fact → producer mapping → transactional outbox
→ delivery transport → Audit receiving adapter → RecordAuditEntry
→ Audit entry + inbox marker in one transaction
```

The producer commits state and required outbox messages together. Its dispatcher
delivers committed messages afterwards. Audit validates the external contract,
maps it into its own command input and commits evidence with a deduplication
marker keyed by consumer/event. Repeated delivery can then be recognized without
appending duplicate evidence. Required messages must not be published only after
an independent state commit: a crash between those operations would lose them.

Bento selects an in-process memory transport or NATS JetStream through generic
delivery ports. The memory transport can still sit above persistent outbox/inbox
storage; memory transport and memory persistence are separate choices. Delivery
is at-least-once and consumers must tolerate replay/reordering. Audit is eventually
updated after the producer's commit.

Proposed placement here: `contracts/events` owns versioned wire schemas;
module `domain` owns internal facts; module `infra/events` owns mapping and
receiving; generic `pkg/events` owns envelopes and delivery mechanics. Account
and Identity need no Audit domain/application dependency. The first local
transport can run in-process; persistent delivery and broker adapters require
their own implementation and conformance/crash tests.

Limit at the reference inspection: `pkg/events`, producer/consumer adapters and persistence remain
reserved. The next question is which first business command needs an atomic
state/outbox port. Documentation links were checked with `make notes-check`;
that check does not prove event delivery.

## Client realtime and broadcast — reference inspection, 2026-10-12

Claim: a selectable Realtime module can consume committed integration events
and deliver recoverable client hints through its own broadcast and socket seams.

Origin/evidence: Bento's [Realtime design](../../../../../bento/backend/REALTIME.md),
[broadcast port](../../../../../bento/backend/go/pkg/broadcast/broadcast.go) and
[Realtime application ports](../../../../../bento/backend/go/internal/realtime/app/ports.go).
Its consumers map integration messages to hints; the broadcast bus fans them
out to gateway instances, which serve their connected clients. Memory and core
NATS implement broadcast; durable event delivery separately uses memory or
JetStream. This is reference source inspection, not mobile runtime verification.

Conceptual proposed flow:

```text
Committed integration event
  → Audit consumer → durable evidence
  → Realtime consumer → broadcast port → WebSocket gateway → native app
```

Durable event delivery reaches independent consumers and supports replay.
Ephemeral broadcast reaches current subscribers, with bounded payloads/queues
and recovery through refetch. The WebSocket is the client-facing connection;
module-to-module event delivery remains behind its existing ports. Neither a
live socket nor successful broadcast acceptance establishes durable storage.

Proposed owners: generic `pkg/broadcast` for fan-out mechanics;
`internal/realtime/app` for publishing/access ports;
`infra/events` for event-to-hint mapping; `transport/websocket/v1` for connection
protocol/lifetime. Native features depend on a narrow live-update service whose
adapter owns the socket. The first connected example should authorize an account
topic, send a changed hint, refetch via HTTP and recover after reconnect. Presence,
bidirectional collaboration and streaming can extend that contract separately.

For this mobile blueprint, recommend session/foreground-owned socket lifetime,
push notification integration and refresh on resume. Apple's
[background execution](https://developer.apple.com/documentation/UIKit/about-the-background-execution-sequence)
describes suspension; [background notifications](https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app)
provide a best-effort wake signal. Android's
[Doze/standby guidance](https://developer.android.com/training/monitoring-device-state/doze-standby)
recommends FCM for backend messaging instead of individual persistent connections.
These sources support lifecycle design; background delivery is not guaranteed.

Limit at the reference inspection: no Broadcast/Realtime/socket/push implementation is added by this
discussion. This should be an optional connected capability, verified against
trusted session scope, reconnect gaps and the chosen provider. Documentation
links were checked; source inspection does not prove socket or mobile behavior.

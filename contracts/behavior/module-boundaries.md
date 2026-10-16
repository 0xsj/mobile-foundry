# Go module ownership and communication

Status: Established blueprint policy, 2026-10-12; updated 2026-10-13. Import
ownership, local capabilities, Account events and Audit now run in the
[Account/Audit workflow](account-audit.md), with
[PostgreSQL persistence](postgres-persistence.md). Remote invocation and Realtime
remain planned. The general policy extends beyond that implemented subset.
The [Identity/session slice](identity-sessions.md) adds principal authentication,
an API-owned credential capability and principal-scoped Audit facts; it leaves
Account scope with its owner.

The [capability catalog](../../backend/go/CAPABILITIES.md) lists selectable
business modules and generic runtime responsibilities.

## Ownership and dependency direction

Each module owns its domain rules, application commands/queries, narrow ports,
repositories, schema/migrations and adapters. Domain code uses its own values
and rules. A workflow needing another module's behavior does so in the
application layer through an application-owned port.

Conceptual call path:

```text
HTTP adapter → application command/query → own domain
                        ↓
                 consumer-owned port
                        ↑ implements
                 infrastructure client → versioned contract
                                            ↓
                                  provider boundary adapter
                                            ↓
                                  provider command/query → own domain
```

Modules never import another module's domain, application, repository or
infrastructure. Process composition selects module builders; a module's root
wiring selects its own adapters. Shared `pkg` code never imports `internal`.
Contracts share boundary data/semantics, not aggregate implementations or
business policy. Consumers map admitted wire values into their own types.

Store ownership is independent of process placement. Repositories access only
their owner's data: no cross-module SQL joins, foreign keys or transactions.
Cross-module references are admitted identifiers, not permission proof. If two
rules must commit atomically, place that invariant with one owner or define a
workflow with explicit intermediate states.

## Three communication mechanisms

| Need | Mechanism | Semantics |
| --- | --- | --- |
| An answer before the current use case can proceed | Synchronous capability | Explicit request/response through the consumer's port |
| Independent reactions to a committed fact | Integration event | Versioned fact with outbox/inbox and at-least-once delivery |
| A recoverable signal to currently connected recipients | Broadcast and Realtime | Ephemeral fan-out; authorized client socket; recovery by authoritative read |

Use the smallest mechanism matching the requirement. A WebSocket is the
client-facing connection. It is not the channel through which Account invokes
Identity or reliably records Audit evidence.

## Synchronous capabilities

The provider owns a named/versioned request and response in
[capabilities](../capabilities/README.md). The consumer declares its own
application interface. Its `infra/capabilities` client implements that interface
using a generic invoker. The provider's `transport/capability/v1` adapter admits
wire input and delegates to its own command/query.

Composition registers local providers and gives each consumer an invoker bound
to its module identity. Start with an in-process registry. A future remote
adapter can use the same contracts without changing either module's application
code. Apply request/response validation, public failure projection, allowed
caller policy and bounded deadlines to local calls as well as remote calls.

Initial policy: cap each invocation at two seconds or the caller's remaining
deadline, whichever is shorter. Preserve cancellation. No automatic retry;
a mutating capability needs explicit operation identity/reconciliation semantics
before replay is safe. These policies are planned acceptance criteria.

Require selected modules to declare provided/required capabilities and refuse
startup when required providers cannot be resolved. `module.Unit` now declares
Provides/Requires and assembly resolves local requirements before workers start.
Remote routing additionally
requires authenticated service identity and an admitted operation context.
Never treat a client-supplied module name as that identity.

An unavailable provider, timeout or malformed response stays a dependency
failure. It must not become “not a member,” “denied,” “no limit,” or “not found.”
Only a successful, admitted negative answer establishes that result. Retain
Go's ordinary `(value, error)` convention inside the application.

For example, a Files command can depend on its own `AccountAccess` port.
An adapter asks Account whether the trusted principal may perform the declared
action in the account scope. Account checks its own current membership policy.
The wire name `account.access.v1` is illustrative until its schema is written.
Identity credential resolution belongs at the trusted edge; business handlers
receive an admitted principal/scope rather than passing raw credentials around.

A synchronous answer is a snapshot, not a cross-module lock. Workflows requiring
stronger consistency must define the permitted race or have the authoritative
owner admit the operation. Async projections are unsuitable as the sole source
for an immediate revocation requirement unless that lag is explicitly accepted.

## Integration events and Audit

Domain facts are internal values owned by the producing module. Its event
adapter maps selected facts to versioned [integration contracts](../events/README.md).
The command's owned transaction commits state and required outbox records
together. Publishing follows that commit through a delivery port; it never
substitutes for the atomic state/outbox write.

Conceptual Account rename flow:

```text
Account command → Account state + outbox, one commit
  → dispatcher → in-process event transport
      → Audit receiving adapter → RecordAuditEntry
          → Audit evidence + inbox marker, one commit
      → Realtime receiving adapter → account-changed hint
          → broadcast → authorized WebSocket → native HTTP refetch
```

Audit validates the integration contract and constructs its own command input.
Account does not call an Audit repository or import its application. An inbox
marker keyed by consumer identity and event ID deduplicates delivery. Separate
consumers receive the same event independently; Audit accepting it must not
consume Realtime's copy.

Delivery is at-least-once. Consumers tolerate duplicate delivery and define
ordering/version rules; no global event order or exactly-once execution is
assumed. Retry/dead-letter handling is explicit and bounded. A committed Account
change can succeed while Audit is temporarily unavailable; evidence catches up
through delivery. Operations requiring durable synchronous evidence must define
that stronger guarantee separately.

Use a stable event ID across retries and carry version, producer, occurrence
time, relevant aggregate/account identifiers and admitted causal attribution.
Payloads include only the selected public fact. Credentials, secrets and private
error causes are excluded. Required fields, actor/system attribution and
consumer identities are fixed with each concrete schema, not guessed in shared
runtime code.

Keep outbox/inbox adapters with their module's persistence owner. An in-process
memory transport can carry records from persistent storage; memory transport
does not imply memory persistence. A memory-only demonstration proves wiring,
not recovery after process loss. A later broker changes delivery adapters while
the producer/consumer contracts remain stable.

## Broadcast and client realtime

Generic broadcast owns bounded ephemeral publish/subscribe mechanics. Realtime
owns subscription/access policy and connection lifecycle, maps admitted events
into [client hints](../realtime/README.md), and serves socket clients through its
transport adapter. Domain modules have no socket dependency.

The first protocol should carry account-scoped changed hints. Clients refetch
authoritative HTTP state on a hint and after reconnect/resume. Delivery gaps
and duplicates are normal recovery cases; a hint is not a commit receipt or
offline synchronization log. Audit may lag an Account hint: an Audit-specific
hint must follow Audit's own evidence commit so its read has an appropriate
trigger.

Authorize connection and every subscription using trusted session/account scope;
define revocation rechecks, resource limits, slow-client policy and cleanup.
Native session/foreground owners manage the live-update adapter and refresh
recovery. Push delivery and richer presence/streaming protocols are separately
selected extensions.

## Workflows spanning modules

An application coordinator owns progress, idempotent steps and any compensating
actions for a multi-module workflow. It uses the same capability/event boundaries
and exposes intermediate/failure states. Do not nest unbounded synchronous
chains or invent a transaction across independently owned stores.

For example, optional personal-account provisioning after identity creation can
react to a committed fact and report provisioning pending until Account confirms
completion. A lost response never establishes rollback or authorizes a blind
repeat of a write.

## Placement and acceptance

```text
contracts/capabilities/          # Versioned synchronous wire contracts
contracts/events/                # Versioned integration facts
contracts/realtime/              # Client protocol and hint contracts
backend/go/pkg/capability/       # Local registry/invoker; remote adapter planned
backend/go/pkg/events/           # Local delivery/dispatcher; outbox/inbox owned by stores
backend/go/pkg/broadcast/        # Generic live fan-out, planned
backend/go/internal/<module>/
  domain/                       # Own values/rules/internal facts
  app/                          # Own ports and use cases
  infra/capabilities/            # Consumer port implementations
  infra/events/                  # Producer mapping and receiving adapters
  transport/capability/v1/       # Provider contract admission
  infra/<store>/                # Owned repositories/outbox/inbox
  module.go, build.go            # Own composition
```

Extend the existing architecture checks with each implemented seam. Verify
contract admission and failure parity across adapters; capability caller/deadline
policy; transaction rollback/crash recovery; independent consumers/deduplication;
and scoped socket admission/reconnect recovery. Static imports alone cannot
establish SQL isolation, durable delivery or authorization.

The [runtime contract](go-runtime.md) describes current implementation. The
[pattern note](../../backend/go/notes/patterns/ports-and-module-composition.md)
records the source investigation behind this policy. Each implementation slice
adds concrete schemas, fixtures and observed acceptance evidence.

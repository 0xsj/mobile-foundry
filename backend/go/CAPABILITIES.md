# Backend capability catalog

Status: Blueprint policy established 2026-10-12; updated 2026-10-17. Runtime,
Health and the [Account/Audit workflow](../../contracts/behavior/account-audit.md)
are implemented, including local capabilities/events and
[PostgreSQL persistence](../../contracts/behavior/postgres-persistence.md).
[Identity/session authentication](../../contracts/behavior/identity-sessions.md) is
implemented for memory/PostgreSQL, including personal bootstrap and owned listing;
[native authority/account adapters](../../contracts/behavior/go-native-session.md)
are verified in the catalogs. [Remote Notes/receipts](../../contracts/behavior/remote-notes.md)
now supply the connected data exemplar. [Files/Media](../../contracts/behavior/files-media.md)
adds owned metadata/content, receipts and Audit facts with native catalog consumers. Remote module routing and other business
modules remain planned/reserved.

Select capabilities for a product rather than copying the whole catalog. Each
business module owns its domain, application handlers, ports, adapters, data and
versioned boundary contracts. The [module boundary policy](../../contracts/behavior/module-boundaries.md)
defines how they collaborate. Runtime mechanics remain in generic packages.

## Business capabilities

| Module | Owns | Typical collaboration | Selection |
| --- | --- | --- | --- |
| Identity | Principals, profiles, authentication methods, sessions, verification and recovery | Resolves credentials for the edge; publishes session/profile/lifecycle facts | Connected core |
| Account | Personal/team accounts, ownership, membership, invitations and account lifecycle | Provides current account access; publishes account/membership facts | Connected core |
| Audit | Selected activity/security evidence, actor attribution and scoped evidence queries | Consumes Identity/Account and other committed facts through its own receiving adapters | Connected core |
| Notes | Replaceable versioned data exemplar and immutable command receipts | Authorizes through consumer-owned AccountAccess; owns memory/PostgreSQL data and replay | Connected reference feature |
| Authorization | Permission vocabulary, scoped roles, grants and permission decisions | Uses declared ownership/membership inputs; provides explicit permission decisions | When account roles alone are insufficient |
| Realtime | Authorized subscriptions, connection lifetime and recoverable live updates | Consumes events, maps hints, broadcasts to WebSocket gateways | Live connected experiences |
| Notifications | Inbox/read state, preferences, device delivery registrations and outbound delivery | Consumes relevant facts; selects push/email adapters | Inbox or outbound messaging |
| Files | Metadata, ownership, upload/download admission, immutable content and deletion | Checks owner access; selects memory/private-directory Objects and memory/PostgreSQL Store; publishes selected Audit facts | Implemented local media foundation; folders, processing and quotas remain planned |
| Billing | Provider customer/purchase references, verified purchases, subscriptions and payment lifecycle | Publishes verified commercial facts; adapters own provider protocols | Paid products |
| Entitlements | Product access, plan features and configured limits | Consumes billing facts; answers access/limit queries | Plans, trials or feature gating |
| Usage | Metered quantities, reservations, consumption and release/settlement | Supports admitted operations and publishes usage facts | Quotas or metered products |
| Integrations | API credentials, external connections and webhook subscriptions/delivery | Exposes selected contracts; reacts to approved events | External clients and automation |
| Privacy | Export/erasure requests, progress and completion | Coordinates explicit module-owned export/erasure capabilities | Products needing these workflows |
| Operations | Maintenance policy and privileged operational controls | Provides maintenance decisions; observes bounded operational status | Administrative/production tooling |

Identity answers who is authenticated. Account answers who belongs to a data
scope and owns its membership rules. Authorization answers whether a particular
action is permitted. Begin with Account's own role policy; introduce richer
Authorization when a product needs it. Avoid two independently editable sources
of membership truth.

Audit is independent evidence, not a dump of every event payload. Select audited
facts, permitted fields, access and retention deliberately. Privacy coordinates
requests but each owner decides how its data is exported or erased under the
product's retention policy.

Billing records verified commercial state; Entitlements interprets product
access; Usage measures consumption. Keep these separate so a provider change
does not change a feature's permission/limit contract. Native store integration
is a future provider adapter, with its own verification workflow.

Search, AI, collaboration, media processing and product-specific domains can
extend this catalog. Add a module when it has independent rules/data and useful
contracts. A search index or cache can also be an adapter within its owner.

## Shared runtime capabilities

| Package/responsibility | Status | Boundary |
| --- | --- | --- |
| `errors`, `clock`, `id` | Implemented | Typed conditions, time/ID ports and values |
| `env`, `config`, `logger` | Implemented | Validated startup configuration and process diagnostics |
| `httpx` | Implemented | HTTP admission/projection, request metadata and header readers |
| `principal` | Port, deny/development adapters and owned Identity resolver implemented | Trusted credential resolution; selection stays at composition |
| `module` | Implemented | Selected builders, routes, readiness, stopping all workers and reverse resource cleanup |
| `persistence/postgres` | Implemented | Verified pools, bounded transactions, uncertain-commit projection and checksummed migrations; schemas/repositories stay module-owned |
| `capability` | Local registry/invoker implemented | Versioned invocation, validation, caller policy and cooperative deadlines; remote adapter planned |
| `events` | Local delivery/dispatcher implemented | Envelopes and independent consumers; memory/PostgreSQL outbox/inbox stored by their owners; broker transport planned |
| `broadcast` | Planned | Bounded ephemeral publish/subscribe fan-out |
| Jobs/scheduling | Planned as needed | Execution/retry mechanics; jobs and their rules stay with their owner |
| Telemetry/service authentication | Planned as needed | Operation observation and authenticated remote module calls |

Health is the implemented runtime exemplar. It owns live/ready HTTP projection,
with no business aggregate. `httpx` idempotency/ETag readers do not implement
replay or concurrency policies by themselves. Assembly now registers capabilities/
subscriptions and resolves required providers before freezing registries and
starting selected workers.

Provider implementations belong behind the owning module's port:
`infra/memory`, `infra/postgres`, or `infra/<provider>`. Shared packages contain
mechanics rather than an inventory of business services. Supabase/Firebase local
stacks remain independent profiles until a concrete adapter satisfies the
selected native/backend contract.

## Common combinations

| Product shape | Candidate selection |
| --- | --- |
| Personal offline tool | Native UI/local data; no backend required |
| Connected personal/team app | Runtime + Identity + Account + Audit + product domain |
| Live workspace/media product | Connected core + Realtime + Files; Notifications when needed |
| Paid or metered product | Connected core + Billing + Entitlements; Usage for measured limits |
| Integration-heavy product | Connected core + Integrations; Authorization for richer scoped access |

These are planning combinations, not registered CLI options. Privacy and
Operations are selected when their workflows are required. Each combination
must declare and resolve the contracts its modules require.

Offline synchronization also needs native pending work, account partitions,
server versions, idempotency and conflict/reconciliation rules. A WebSocket
provides a live signal; it does not supply that workflow. Define sync per product
domain before extracting generic mechanics.

## Construction order

Account creation/read/rename, owner access, local capability routing and Audit
delivery/replay are implemented with memory/PostgreSQL adapters and an explicit identity
fixture. PostgreSQL checks verify atomic rollback, replay, leases and process recovery.
Identity now supplies real password/session credentials under explicit selection,
with principal-scoped Audit, expiry, rotation/reuse and durable revocation checks.
Account now supplies [personal bootstrap and owned discovery](../../contracts/behavior/account-bootstrap.md),
with device-owned selection through guarded lookup and migration/restart evidence.
See [construction](../../docs/blueprints/account-audit.md). Next:

1. Extract and independently verify a connected native/backend combination.
   Standalone Go extraction is verified through CLI 0.4.0; native shells can be
   co-located with the API but their auth/data composition remains the next gate.
   Catalog sessions/accounts and remote Notes CAS/receipt recovery are verified;
   durable native pending input, merge rules and synchronization need deliberate
   additional capabilities.
2. Add optional Realtime: account-scoped changed hints, bounded connections,
   reconnect and HTTP refetch. Push, presence and collaboration extend this
   contract independently.
3. Register only independently verified backend combinations in the CLI; Go's
   full implemented-module selection has passed this gate.
   Follow [starter readiness](../../docs/STARTER-READINESS.md) for that evidence.

Identity supplies the trusted principal for the tested backend workflow. Account
membership/invitations, recovery, richer auth providers and connected native
starter acceptance remain separate. Development credentials remain an independent fixture.

Read [module communication](../../contracts/behavior/module-boundaries.md), then
the [Go pattern note](notes/patterns/ports-and-module-composition.md) for the
reference investigation and the difference between current and planned evidence.

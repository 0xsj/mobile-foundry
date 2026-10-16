# Account → Audit construction map

Implemented 2026-10-13. Read [behavior](../../contracts/behavior/account-audit.md)
for guarantees/limits and [HTTP](../../contracts/http/account-audit-v1.md) for the
wire contract. Memory and [PostgreSQL](../../contracts/behavior/postgres-persistence.md)
implement the same ports. [Identity](identity-sessions.md) now supplies a real
credential authority. [Personal bootstrap/discovery](../../contracts/behavior/account-bootstrap.md)
adds repeatable account scope and device-owned selection. Socket/native provider
adapters and CLI registration remain separate.

## Source owners

| Owner | Construction |
| --- | --- |
| `internal/account/domain` | Private Account values, canonical creation/restoration, rename and minimal internal facts |
| `internal/account/app` | Store/Directory ports and failures; create/rename/bootstrap commands; get/access/list queries |
| `internal/account/infra/memory` | State + outbox atomicity, CAS, retained delivery attempts/readiness |
| `internal/account/infra/postgres` | Transactional state/outbox/personal mapping, owner-filtered listing, locked CAS and durable delivery leases |
| `internal/account/infra/events` | Internal fact → versioned integration envelope/data |
| `internal/account/transport` | HTTP credential/body/version projection, owner-bound account cursors; synchronous access provider |
| `internal/audit/app` | Record/List handlers; its own Store and AccountAccess ports |
| `internal/audit/infra/events` | Admitted Account contract → own evidence input; independent consumer identity |
| `internal/audit/infra/capabilities` | Account access client implements Audit's port, with no Account implementation import |
| `internal/audit/infra/memory` | Evidence + inbox atomicity, replay conflict, scoped keyset reads |
| `internal/audit/infra/postgres` | Transactional evidence/inbox, replay fingerprints and commit-ordered cursor allocation |
| `pkg/persistence/postgres` | Pool, transaction/error and migration mechanics with no business catalog |
| `migrations/<module>`, `cmd/migrate` | Embedded module SQL; explicit application and read-only verification |
| `pkg/capability` | Registry, caller-bound invoker, deadline and JSON/public failure boundary |
| `pkg/events` | Generic envelope, independent memory delivery, dispatcher/worker lifecycle |
| `pkg/module` | Provides/Requires/Subscriptions/Start/Stop; validate before start, stop every worker before resource close |
| `cmd/server` | Explicit Account/Audit selection and optional development credential resolver |

Each module's `module.go` wires its ports; `build.go` selects memory adapters and
`postgres.go` exposes a builder receiving explicit pool/migration configuration.
The process imports module roots. Domains have no event runtime dependency;
application handlers never import concrete adapters or the other module.

## Walk through the seams

1. Create an account through HTTP. Transport admits the credential and name;
   the command constructs canonical owner state and an internal fact.
2. The memory repository maps the fact and atomically stores state/outbox.
   The response returns committed account metadata while delivery is independent.
3. The worker publishes the stable message. Audit validates and maps it into
   its own Record command, which commits evidence/inbox together.
4. Read scoped Audit evidence. Its List handler asks its own AccountAccess
   port; the client invokes the local `account.access.v1` provider. The Account
   query checks its own store, with explicit negative versus failed answers.
5. Rename with a stale/current ETag and inspect evidence. Replay a delivered
   event through the tests and observe one Audit append per consumer/event.
6. Bootstrap the personal account. Concurrent/repeated calls return one mapped
   account; only creation emits a fact. Ordinary owner accounts stay separate.
7. List owned accounts and validate a selected ID through the guarded GET.
   No server-global selected account or Identity store dependency is introduced.

## Verification and next provider

`make check test smoke` from `backend/go` checks static boundaries, race-enabled
unit/real HTTP scenarios and separately compiled process behavior. Portable name
fixtures are loaded in Account's module-root test so domain test source retains
the same infrastructure import restrictions as domain production source.

`make integration-test` owns a disposable PostgreSQL container. It verifies
transaction rollback, CAS, inbox replay, stable lease recovery, cursor isolation
and forced API crash/restart. It now also checks retained 0001 upgrade, personal
mapping rollback, concurrent bootstrap and recovery/discovery through actual
Identity credentials. The latest full run used isolated local PostgreSQL 14.24.
No local volume is reset. Commit uncertainty is
classified with an injected unit-test failure, not a real network-cut drill.
Native session/account services now connect through [Go adapters](go-native-session.md).
Next, supply remote account data and receipts. Independent
CLI extraction remains the gate in [starter readiness](../STARTER-READINESS.md).

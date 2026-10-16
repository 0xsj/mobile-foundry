# Personal account bootstrap and owned discovery

Implemented in the Go memory/PostgreSQL profiles, 2026-10-13. This extends
[Account/Audit](account-audit.md) and supplies account scope for a future native
Go authority adapter. It does not change Identity's credential/session ownership.
See [Account HTTP v1](../http/account-audit-v1.md) for the wire contract.

## Explicit personal account

An authenticated principal can bootstrap one personal account. Account owns a
principal → personal account mapping. The winning request atomically commits
that mapping, a new owner account and its existing `account.created.v1` fact.
Concurrent successful requests return the same account; exactly one reports
creation. Failures before commit leave none of those three writes behind.

Bootstrap accepts an admitted account name on every request. The first committed
name wins. Repeats return the current mapped account, including later renames,
without changing its version, timestamps or facts. They never adopt an arbitrary
account previously created by `POST /v1/accounts`. Existing databases gain an
empty mapping through migration `0002`; no account is reclassified automatically.

This is semantic idempotence keyed by the trusted principal, not generic request
receipts. `Idempotency-Key` remains unsupported. After an uncertain bootstrap
response, a caller may repeat a valid bootstrap to resolve its mapped account.
Dependency/credential failures remain failures; success is not guaranteed while
dependencies are unavailable. Ordinary create and rename retain their existing
write-uncertainty rules. No new fact is emitted for a repeated bootstrap.

PostgreSQL's owner key chooses the winning transaction. A deferred module-owned
foreign key enforces account/owner agreement at commit. A competing request
reads the winner in a separate Read Committed statement after conflict resolution.
Memory uses one lock. No Identity table, cross-module transaction or foreign key
is involved. A valid trusted principal is the input; there is no directory lookup
into Identity from Account.

## Owned listing

Discovery returns only accounts owned by the admitted principal, including its
personal account and ordinary owner accounts. Empty ownership returns a successful
empty page; a store outage must not become that answer. Pages use ascending
canonical UUID order and an exclusive UUID continuation, default limit 20,
maximum 100. The cursor binds version, owner and position and is not a credential.
Every page uses the current trusted principal and filters ownership in its store.

UUID order is not creation order. Pages are live reads, not a retained snapshot.
Accounts created at or below an already consumed position may be missed until a
fresh listing. Names/versions may change between pages. No total count, membership,
invitation, permission hierarchy or deletion workflow is implied.

## Device-owned selection

Select by reading `GET /v1/accounts/{id}` with the current credential. Successful
owner admission returns the real account ID/name/version; foreign accounts fail
403 and absent accounts fail 404. The server has no principal-global selected
account and does not bind an Identity session to that account. Re-check the
chosen account on restoration/renewal; bootstrap resolves the personal default
when a client explicitly needs one.

A native authority can compose sign-in → bootstrap/list → guarded account lookup
→ account-bound native grant. Restoration must retain its stored account ID,
rather than silently selecting a different default. A scope change needs a new
native lease and cleanup; accepted writes retain their original account target.
This sequence is specified for the next adapter; it is not connected yet.

## Evidence and limits

Race-enabled memory/PostgreSQL conformance covers twelve competing bootstraps,
one winner, no adoption/orphan/duplicate fact, current renamed state, owner-only
pagination and cancellation. HTTP checks cover admission, foreign cursors,
HEAD and guarded selection. Disposable PostgreSQL acceptance covers retained
`0001` upgrade, event-failure rollback, account/owner FK rejection and recovery.
Compiled Identity processes verify mapping/listing/selection after forced API
crash. The latest run used isolated local PostgreSQL 14.24, not a new Docker run.

Memory state still disappears on exit. Native authority adapters, team membership,
backend CLI extraction and generated connected-product acceptance remain pending.

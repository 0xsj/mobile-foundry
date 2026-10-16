# Idempotent bootstrap and owned discovery

A module-owned unique owner mapping can make personal-account bootstrap repeatable
without introducing generic write receipts or a globally selected account.

Origin: Account scope slice, 2026-10-13. Source inspection, race-enabled memory/
PostgreSQL conformance and compiled API crash/restart acceptance. The database
execution used local PostgreSQL 14.24 and pgx v5.11.0. Primary background:
[Read Committed and ON CONFLICT](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED)
and [deferred foreign keys](https://www.postgresql.org/docs/18/sql-createtable.html).

## What and why

An ordinary create admits a new identity on every accepted call. Its missing
response cannot be resolved by blindly repeating it. Personal bootstrap has a
different product rule: one principal has one explicitly mapped personal account.
That stable logical key permits repeated requests to converge on existing state.

The [Bootstrap command](../../internal/account/app/command/bootstrap.go) constructs
a canonical candidate and asks its Directory port to atomically create or return
the mapped account. First commit wins; later requests return current state,
including renames. They do not overwrite a name, infer a default from an arbitrary
workspace or emit another creation fact. Name admission still runs for repeats.
Dependency failure remains a failure, not a fabricated account or empty page.

The PostgreSQL adapter claims the owner key before inserting state/outbox. Its
module-owned account/owner foreign key is deferred to commit, so the successful
transaction can insert the referenced account after claiming the mapping. A
failed outbox append rolls back all three values. The constraint also rejects
a mapping whose owner differs from the account's owner. Identity owns no part
of this transaction.

## A subtle visibility boundary

Under Read Committed, `ON CONFLICT DO NOTHING` can lose to another transaction
whose committed row was not visible to that statement's original snapshot.
The losing request reads the mapped account in a **separate statement** after
conflict resolution. Combining insert and fallback read into one CTE would not
give that read a fresh statement snapshot. This choice follows the documented
isolation behavior and is exercised by competing bootstrap calls.

Excerpt from [the adapter](../../internal/account/infra/postgres/directory.go):

```sql
INSERT INTO account.personal_accounts(owner_id,account_id) VALUES($1,$2)
ON CONFLICT(owner_id) DO NOTHING RETURNING account_id::text
```

No returned row means another mapping won. The next statement looks up the
owned account. A lock timeout, cancellation or uncertain commit remains an error;
the helper does not hide it with an automatic transaction replay. A later explicit
bootstrap can reconcile the personal mapping after an uncertain response.

## Discovery and selection are different concerns

Listing filters owner and sorts by canonical UUID at the store boundary, with
an exclusive UUID cursor. Query requests one extra row to determine continuation
and checks ownership/order before projecting the returned page. The HTTP cursor
binds version/owner/position, but is not a credential or snapshot. Newly inserted
random UUIDs behind an already consumed position require a fresh listing.

Selection is the existing guarded lookup, not a server-side preference mutation.
Conceptual example: two devices can select different owned workspaces while
using independent sessions for the same principal. On restore/renewal, each
native authority must validate its saved account ID. Substituting a new default
would change data scope and break the existing lease/accepted-write rules.
The Go native authority adapter remains future work.

## Evidence, gotchas and actual use

[Provider conformance](../../tests/account_directory_test.go) exercises twelve
concurrent calls, one winner, distinct ordinary/personal accounts, current rename,
three expected facts, ordered owner pages and cancellation on memory/PostgreSQL.
HTTP checks cover invalid input, foreign cursors, selection denial and HEAD.
[Database acceptance](../../tests/integration/postgres_test.go) upgrades retained
0001 state without adopting accounts, injects duplicate event identities to prove
atomic rollback and rejects a cross-owner mapping at commit.
[Compiled processes](../../scripts/postgres_integration.py) recover the same
personal account and list/select owned workspaces after SIGKILL.

`make check test smoke`, isolated local `make integration-test` and root
`make notes-check` are the slice's checks. These do not establish team membership,
native connectivity, backend CLI extraction, snapshot listing or database failover.
Memory still loses its mapping on exit. There is no account deletion path yet;
adding one must explicitly address the personal mapping and retained fact policy.

Related: [Account walkthrough](../modules/internal/account/README.md),
[principal/session/account boundaries](principal-session-and-account-boundaries.md),
[transaction/lease ownership](transactions-leases-and-committed-cursors.md).
The [native authority](../../../../contracts/behavior/go-native-session.md) now
persists rotations before account lookup and active lease publication. Next:
permanent account-unavailability UX, remote data receipts and generated acceptance.

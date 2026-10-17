# Durable pending Notes changes

This capability adds a native recovery journal to the connected Daily starter.
It builds on [remote commands/receipts](remote-notes.md) and
[session-bound access](account-access.md). Guest local CRUD remains separate.

## Port and record

Services owns PendingNotesStore, PendingNotesScope and PendingNoteRecord. Data
supplies SQLite; Memory is an explicitly transient interchangeable adapter.
A scope is the admitted deployment authority, original principal and account.
Credentials and session lease epochs never enter the journal. Renewal can recover
the same scope; another deployment, user or account cannot load that slot.

Each scope has one slot containing the exact admitted command: original ID,
action, target, title bytes and expected version. Its state is pending, confirmed
with matching receipt, or rejected with a known refusal. Pending input cannot be
replaced by another command. Identical-command replay is checked before CAS;
reuse of an ID with different input is a conflict. A new command replaces a
terminal slot only with that slot's expected command ID. Late settlement must
match the current original command and cannot erase newer input.

Confirm validates receipt owner and exact command input/action/version. Reject
admits only notes.version_conflict and notes.not_found from execute, never from
receipt lookup. Confirmation/rejection transitions cannot contradict a terminal
result. Terminal data remains until a new explicitly staged command replaces it.
These slots are recovery records, not an event history or multi-item outbox.

## Persist before effect

The product stages the command durably before its first HTTP attempt. Failure or
uncertain local acknowledgement blocks dispatch and new writes until reload.
A crash after staging leaves recovery evidence even if HTTP never happened.
A crash after server commit but before local confirmation leaves the same pending
command for receipt lookup. A confirmed receipt must settle durably before the
owner clears pending; a failed follow-up list does not erase the receipt.

Bind, foreground, credential restore, refresh and process launch load recovery
and read Notes but never execute a saved command. The user can Check receipt or
Retry same change; retry reuses the exact original command through a newly admitted
lease for its original scope. A missing receipt is an observation and keeps the
change pending. Unknown/auth/network failures also retain it. No second command
is admitted while pending or while recovery storage is unreadable.

Feature replacement cancels old work and suppresses late presentation. Storage
may have committed before cancellation; it remains bound to the original slot.
Sign-out keeps that device's scoped changes. There is no implicit account switch,
new command ID, rollback, discard, merge or background replay.

## Disk boundary

SQLite owns short transactions, including initialization, compare/stage and
settlement; no transaction contains network work. Instances serialize through
native file locking. The v1 store has its own kind marker and user_version=1.
Only an empty unversioned database can initialize. Corrupt/foreign/future stores
and invalid records are refused and retained, never deleted/reset implicitly.
Android supplies a refusing corruption handler rather than default deletion.

The owned JSON envelope is pending-notes.v1 with exact root/scope/command/receipt
fields, strict UTF-8, admitted models and consistent status. Payloads are at most
16 KiB. The store defaults to 256 scope slots and refuses growth at capacity;
existing identical replays and terminal replacement in an existing slot still work.
No pruning, expiry, destructive migration or erasure API is selected.

Generated iOS composition uses a private Application Support subdirectory,
excluded from backup, with complete-until-first-authentication file protection.
Android uses application noBackupFilesDir. These are native private files, not
application-encrypted content vaults. App removal can erase recovery data.

## Acceptance and limits

Shared fixtures exercise record admission and exact input preservation. Native
SQLite checks cover reopen, scope partitioning, terminal CAS, stale settlement,
capacity, competing instances, rollback and corruption/schema refusal. Owner
checks inject lost stage acknowledgement and failed receipt persistence.

The copied connected driver owns a fault proxy in front of its temporary Go API.
It withholds a committed receipt and refuses an unsent first attempt. Actual app
termination/force-stop and relaunch must recover the same ID/input, observe a
missing receipt without clearing pending, and explicitly settle/retry. The proxy
checks original path/body/ID and requires one commit for each scenario.

This supplies durable manual recovery for one change per scope. It does not add
a read cache, offline authentication, automatic sync, multiple queued edits,
conflict merging, background execution, Files/inbox selection or device performance
guarantees. The reference catalogs keep their earlier transient gallery owners;
this slice selects disk recovery in generated connected products.

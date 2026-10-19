# Atomic byte staging before transport

Claim: durable manual transfer recovery requires the original bytes and command
to commit locally before transport, and an acknowledged local removal before the
owner can accept another operation.

Origin: native Files journal, 2026-10-19. The original ephemeral owner could keep
an exact command across renewal but lost its payload on process exit. Extending
its lifetime changes the state machine, not only the storage location. Read
[the contract](../../contracts/behavior/pending-files.md) and
[execution evidence](../../docs/blueprints/connected-files.md) separately.

## What and why

A metadata record pointing to a separately written photo creates two commits.
A crash between them needs orphan cleanup or a reconciliation protocol. For one
bounded photo, storing metadata and bytes in one SQLite row gives one atomic
boundary. It also makes removal compare the complete original intent rather than
a filename that could later refer to different bytes.

Conceptual:

```text
explicit accept → stage original command + bytes → acknowledged local commit
  → exact-lease HTTP → known remote completion → exact journal removal
  → acknowledged local commit → fresh page → another explicit acceptance
```

An uncertain stage must dispatch no HTTP. It may have committed, so keep the
original input and reload or stage the identical record on explicit retry. An
uncertain removal must not unlock new work. On restart, finding the same record
means receipt settlement or original-delete cleanup can be repeated safely; it
does not mean the server action failed.

## Restore is a read, not a dispatcher

Persist the deployment, principal and account, never an expiring bearer token.
Restore credentials separately, bind a current lease, load recovery, and leave the
choice to retry/review with the user. A different account can browse its own page
but cannot mutate or display another scope's pending photo. One global slot
bounds retained input across accounts rather than multiplying a photo allowance
by every visited account.

A disk adapter can finish after cancellation. Check cancellation after stage and
before each HTTP handoff, as well as generation before publication. SQLite's
commit cannot be undone by discarding an old task result. A restored record that
was staged by a cancelled task still requires a new explicit retry.

## Native storage considerations

A 10 MiB photo is not a normal small cursor row. Android's adapter requests at
most 256 KiB using SQLite substr while retaining the transaction, then validates
the reassembled size/hash. The independent device test uses the full 10 MiB
admission limit. Swift reads the bounded blob through SQLite3. Neither design
promises a 10 MiB total heap or disk-file ceiling: imports, copies, SQLite pages
and rollback journals add overhead.

Private/backup-excluded files are not an application-encrypted content vault.
Deleting a row releases reachable recovery state; it does not establish secure erasure of
free pages or deletion of server objects. The review warning must distinguish local
abandonment from completed cleanup. Multi-item/background transfers need their
own scheduling, retention and reconciliation policies.

## Used in and related

Generated Files-only and Files + Inbox compositions inject a separate journal
through the same port. Shared admission, native disk checks, fault-injected owners
and actual app process-restart scenarios provide different evidence; follow the
construction guide for actual counts and host-workaround limits. Catalog examples
remain ephemeral. Read [receipts and byte recovery](file-receipts-and-byte-recovery.md)
and [explicit abandonment](explicit-abandonment-is-not-settlement.md) first.
Next: durable cleanup responsibility, quota/retention and OS transfer ownership.

## Logical request identity versus JSON byte order

The first iOS unsent-content restart exposed a test-boundary error: the proxy
compared raw request-upload JSON. Swift builds that object with a dictionary and
a plain JSONEncoder; a fresh process can change object member order. The final
boundary canonicalizes only that JSON syntax and still compares every original
field/key/version. Content PUT compares raw bytes. Normalizing JSON object order
does not justify normalizing file names, Unicode sequences or photo bytes.
The final native runs pass this boundary on Swift/Kotlin against Go/Rust;
construction records the exact counts, fingerprints and earlier failed attempt.

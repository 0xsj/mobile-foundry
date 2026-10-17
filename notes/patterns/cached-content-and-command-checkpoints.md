# Cached content and command checkpoints

Claim: an advisory read cache can survive connection loss without becoming a
write base when presentation freshness and durable command progress are separate.
Origin: connected native Notes caching, 2026-10-17. Evidence: shared admission
vectors, actual SQLite reopen/refusal checks and generated product acceptance
recorded in [construction/evidence](../../docs/blueprints/notes-read-cache.md).

## What and why

A saved page says what a client observed earlier. It cannot prove membership,
current record versions or whether a later command committed. Keep it behind a
read-cache port and use a distinct cached presentation state. A valid session
gates access; a fresh server read gates new writes. Cache failures must not look
like pending-command loss or prevent use of a successful server response.

The interesting boundary is a deletion followed by a failed read. Without more
evidence, the old disk page can reappear after restart even though the receipt
confirmed deletion. Store the journal command ID and status alongside each saved
page. Restore the journal first and compare that checkpoint before display.

```text
Conceptual: two independent local stores, no distributed transaction.
read page at journal checkpoint A -> save snapshot tagged A
stage command B -> server confirms B -> journal marks B confirmed
next read fails -> snapshot A no longer matches -> hide it
```

This deliberately prefers absence over resurfacing a pre-mutation page. If a
new successful read happened while a command was pending, its checkpoint remains
pending; settling that command invalidates the saved read again. Other clients
can still change data at any time. A checkpoint is not a server revision or sync
protocol, and cached versions never feed writes in this composition.

## Gotchas and alternatives

Expiry is a usability rule. Lazy removal cannot promise physical erasure at a
particular wall time. Clock rollback invalidates a future snapshot rather than
extending its life. Backup exclusion is distinct from encryption; the OS private
container/protection applies. Product-specific erasure and retention need policy.

Cache only a known first page. Persisting later pages without a server snapshot
cursor invites gaps and duplicates; keeping the first page read-only is honest
about its limits. A separate cache warning keeps optional disk errors from
obscuring successful HTTP content or the stricter command-journal requirement.

## Actual use, related and next

Daily's generated Swift/Kotlin products inject SQLiteNotesReadCache; tests can
inject Memory, failed or suspended adapters behind the same port. The existing
PendingNotesScope supplies only the admitted storage tuple, never a credential.
Read [the contract](../../contracts/behavior/notes-read-cache.md),
[intent persistence](persist-intent-before-external-effect.md) and
[live invalidation](live-invalidation-and-pending-writes.md).
Next: which real product needs offline authenticated startup, and what bounded
access/erasure policy would justify exposing account data without revalidation?

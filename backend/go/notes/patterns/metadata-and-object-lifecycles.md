# Metadata and external object lifecycles

Claim: separate metadata/object stores need explicit lifecycle and recovery rules;
a database transaction alone cannot make external byte effects atomic.

Origin: Bento Files source inspection and the first Files implementation,
2026-10-17. Reference design: [Bento Files](../../../../../bento/backend/FILES.md).
Actual scope: [Files walkthrough](../modules/internal/files/README.md) and
[observed execution](../../../../docs/blueprints/files-media.md).

## What and why

Metadata answers ownership and whether content is visible. The object adapter
answers how bytes are put/read/deleted. Pending metadata admits an exact declared
size/digest, while immutable writes prevent completion from checking one payload
and later serving another. Publication requires an atomic metadata version guard
after verification; a preliminary storage read does not reserve the record.

For deletion, reverse the visibility first: commit an inaccessible tombstone,
then remove the object. Removing bytes first could race completion and leave an
available row with missing content. If cleanup fails, keep the tombstone and
reconcile the external effect rather than restoring visibility implicitly.

Conceptual example:

```text
available v2 → deleted v3 + receipt/fact → delete object
                   ↓ cleanup response lost
same key → replay deleted v3 receipt → repeat idempotent object deletion
```

The receipt establishes the metadata result. Successful cleanup is a separate
observation; receipt lookup does not perform it. A crash can leave orphan content,
and a replayed upload request may describe historical pending state after a file
was deleted. Every actual content read/write still checks current authorization
and metadata. Never use a historical receipt as access to private bytes.

## Transferable mechanics

A private directory can write a temporary file, sync it and link it into the
immutable key without replacing an existing object. An existing key is accepted
only after validating its bytes. S3-style staging/promotion is another adapter
strategy; its overwrite, expiry and consistency semantics need their own tests.
No S3 adapter has been implemented here.

Keep filenames, MIME declarations and content out of integration facts/Audit.
Expose only the selected IDs/state/version/time needed by independent consumers.
Synchronization history, notifications and derived thumbnails each need their
own owner and recovery rules; an upload completion event does not implement them.

## Gotchas, use and evidence

Used by Files command/store/object ports and account-bound native retry drafts.
Exact byte/hash admission, detached buffers, concurrent publication, rollback,
scoped replay and API crash/reopen were exercised on memory/private-directory and
PostgreSQL. Logical deletion was observed to close HTTP download access.
These checks do not prove filesystem durability after host power loss, backups,
malware policy, background execution or bounded long-term disk growth. A sweeper,
upload expiry and retention are remaining operational work.

Related: [uncertain writes](../../../../notes/patterns/command-receipts-and-uncertain-writes.md)
and [transaction/lease ownership](transactions-leases-and-committed-cursors.md).
Next: which states should distinguish a logical deletion, pending physical cleanup
and final erasure once automatic cleanup is selected?

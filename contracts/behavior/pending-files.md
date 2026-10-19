# Durable manual Files recovery

Daily's explicit Files capability selects one PendingFilesStore journal through
[the existing Files owner](connected-files.md), independent of Notes and Inbox.
This is one original upload/delete across all scopes in a product installation,
not a transfer queue, offline authentication or automatic synchronization.

## Input and persistence boundary

A pending record admits a canonical UUID command prefix and original authority,
principal and account. Scope contains no credentials or lease epoch. Upload
retains exact specification and bytes, bounded to 10 MiB with verified size/hash.
Delete retains its original nondeleted file/version and no content bytes. Names
retain original UTF-8; Unicode-equivalent text cannot replace accepted input.
Metadata has an owned pending-files.v1 envelope, exact fields and a 16 KiB bound.
Shared [admission fixtures](../fixtures/pending-files/records.json) cover both natives.

Native photo selection remains an unaccepted view-owned draft. Explicit Upload
photo or confirmed Delete accepts an intent. The owner must receive a successful
journal stage acknowledgement before any HTTP for that intent. Failed/uncertain
staging keeps the original input and dispatches no HTTP. A later retry stages the
same input idempotently. Bind/restore/refresh read local state and server pages
only; recovery always requires explicit Retry or Review in the original scope.
Other scopes may browse, but cannot accept another Files mutation or review the
hidden operation. Scope changes never retarget retained input.

Each retry stages the exact original record before transport. Upload recovery
checks its completion receipt before PUT; delete repeats its original command to
finish external cleanup. After confirmed server completion, acknowledged exact
journal removal is required before releasing pending state. A failed removal
retains recovery; a relaunch can repeat receipt settlement or original deletion.
No local receipt is invented. Cancellation/generation checks fence every handoff
from disk to HTTP and prevent old results from publishing after detach.

## Journal ownership

The port exposes load, stage and remove. One global slot refuses a different
record until the current record is explicitly removed. Stage of identical input
is idempotent. Remove compares the whole original record and cannot erase a newer
intent or another scope; absence is an idempotent success. SQLite transactions
serialize independent instances and contain no network operation. Metadata and
bytes commit in the same row, avoiding a separate staged-file/orphan boundary.

SQLite uses its own pending-files.v1 kind and user_version=1, FULL synchronous
commits and rollback journaling. Only an empty unversioned database initializes.
Foreign, corrupt, future or invalid payloads are kept and refused; no destructive
migration, default corruption reset or bypass of unreadable storage is selected.
Android reads payloads through bounded 256 KiB slices in the same transaction,
avoiding a whole-photo cursor row. iOS uses private Application Support/PendingFiles
with backup exclusion and complete-until-first-authentication protection; Android
uses noBackupFilesDir. These are OS-private files, not an application-encrypted vault.

Load must succeed before new changes. An unreadable journal blocks edits even if
an HTTP page succeeds. Recovery input may also remain in memory after a save
acknowledgement is lost; an inconsistent different disk record causes a refusal,
never silent replacement. A failed page still permits review of an already loaded
local record, while fresh server reads gate new mutations.

## Explicit abandonment and limits

[Review/Keep/Forget](connected-files.md) keeps its scope/key/generation fencing.
Forget now waits for exact persisted removal before releasing intent and fetching
a fresh page. A failed removal leaves pending recovery. The consequence warning
still applies: forgetting sends no server mutation, proves no rollback and does
not finish external byte cleanup. Row removal is logical deletion of recovery,
not secure erasure of SQLite free pages, backup erasure or server data retention.

Actual process termination/force-stop must restore pending uploads and deletes,
including bytes that never reached the backend. Restore never dispatches them.
Retry uses the original key/specification/bytes/version without selecting a photo
again. Storage checks cover 10 MiB reopen, exact removal CAS, competing instances,
rollback and corruption/schema refusal; owner checks inject acknowledgement loss,
failed removals, unreadable load and a noncooperative cancelled disk stage.

App removal, local storage loss and unrecoverable corruption can still lose or
block recovery. Current authenticated session/account access is required; this
adds no offline sign-in, background/OS transfer scheduling, multi-item queue,
resumable chunked HTTP, automatic merge, object sweep, retention or public release
acceptance. Catalog retry owners remain ephemeral; this journal is selected by
the generated Files/Files + Inbox product compositions only.

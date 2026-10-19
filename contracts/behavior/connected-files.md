# Optional connected Files

Daily's explicit files capability requires one matching go-connected/Go or
rust-connected/Rust selection. Swift/Kotlin use the same native source for either
backend. Files alone selects Today and leaves Activity as a placeholder;
[the explicit combined selection](connected-media.md) adds Inbox. No implicit Files
selection follows from a copied backend, and files + inbox screens require a
separate registered combination.

The app owns FilesStore, navigation and photo drafts. RemoteFilesRepository is
the swappable domain port. Its HTTP implementation uses the current exact session
lease and the [Files protocol](files-media.md); neither transport nor lease
renewal silently replays writes. Every bind invalidates generation, clears the
visible page/download and starts reads only. Late old results cannot publish.

The native system photo picker stages a draft; a separate Upload photo action
accepts the current scope. Drafts live above async import and are keyed by
authority/principal/account, so scope changes cancel and discard them. Drafts
are not accepted commands and may survive a same-account lease renewal. No broad
gallery permission, camera, editor, export or background transfer is selected.
Admission uses detected supported image MIME, generated name, size and SHA256.
Android reads with a 10 MiB bound; PhotosPicker's transferable Data is checked
after loading. This is not a streaming iOS import memory guarantee.

One pending intent per installation bounds retained input across all accounts.
Generated products select the separate [durable journal](pending-files.md),
which stages exact metadata/bytes before HTTP and removes them only after
acknowledged settlement or explicit abandonment. Catalog retry owners remain ephemeral.
Upload
retains at most 10 MiB with immutable specification and original command prefix;
Kotlin defensively copies arrays. Delete retains the observed file/version and
command. Other accounts can be read while pending, but cannot accept another
mutation. Return to the original scope for explicit recovery. Sign-out hides the
intent; the journal retains it across process exit until settlement or explicit abandonment.

Upload recovery reads the original completion receipt first. A matching historical
completion settles intent even if another client later deleted the file. Only
not_found permits request-upload replay, identical byte PUT and completion under
the original derived keys. Unavailable/forbidden/invalid receipt results stop
recovery. Delete repeats the same original DELETE and precondition; a receipt GET
cannot establish external cleanup. A failed operation keeps its intent; bind,
refresh and process restoration do not resend it.

Pending intent can be reviewed in its original scope while the owner is idle.
The review names the original upload/delete and file. Keep/dismiss leaves it
untouched. Forget local recovery waits for exact persisted removal before releasing retained bytes/command;
it does not establish success, cancel/undo a server action or prove byte cleanup.
The delete warning must say cleanup can remain unfinished. No server command is
sent by forgetting. A new authoritative page is required before a new mutation;
absence from a bounded page is never proof of deletion or rollback. Subsequent
actions use newly observed data and a new key only after explicit acceptance.

Confirmations carry both the original intent key and owner generation. They are
refused while busy, in another scope, after any rebind (including renewal), or
after the original intent has been replaced. Forgetting invalidates outstanding
picker/review tickets and clears preview/failure state. There is no automatic
classification of terminal errors or replacement of the original version/key.

Pending Files use their own marked SQLite journal rather than Notes' journal.
A terminated process restores original input by reads only; explicit retry/review
is required. Failed staging sends no HTTP, and failed local removal retains
recovery. Unreadable/foreign/future stores are preserved and block changes. No
rollback, orphan sweep, secure erasure, automatic conflict replacement or sync is
promised. Read the durable contract for protection, capacity and failure rules.

Lists page 20 rows at a time and retain at most 200. A duplicate page requires a
full refresh before paging/mutation. Downloads verify size/hash/type/version in
the adapter and retain at most one payload; native image thumbnails are bounded
to 1600 pixels along the largest dimension. Non-image files show verified status
without an image preview. Detach clears download bytes; thumbnails and photo
drafts remain view-owned. A confirmation precedes account-wide deletion.

Independent acceptance runs from generated output, builds its selected backend,
uses owned temporary users and actual gallery pickers, and tests completion loss,
receipt-only settlement without reupload, same-command deletion recovery, paging,
an actual competing delete returning 404 followed by keep/forget without replay,
download preview, scope isolation, renewal, sign-out and restored server state.
State-owner tests additionally exercise ignored stale completion/import tickets,
retained byte identity, logical deletion before cleanup retry and duplicate pages.
They also cover terminal version conflicts, stale review confirmations, active
read exclusion and accepting a fresh command only after explicit abandonment.
Readiness requires actual generated native execution, not inventory checks alone.

# Files and Inbox composition

CLI 0.23.0 explicitly registers Daily + files + inbox with one matching
go-connected/Go or rust-connected/Rust selection. Swift/Kotlin use the same native
source for either backend. Existing UI-only, Files-only and Inbox-only selections
retain their own inventories. Workspace/Browse, local-data mixtures and two
connected backends remain unsupported combinations, refused before copying.

The combined inventories reuse [Files](connected-files.md) and
[Inbox](connected-inbox.md) owners verbatim. No common mutation controller replaces
their different recovery rules. ConnectedSessionModel binds each repository to
the same exact current SessionLease. Every detach invalidates and clears each
visible owner independently; accepted Files input stays private and scoped under
the [durable manual recovery](pending-files.md) limit. Neither owner imports or mutates the other.

Today shows Files; Activity shows Inbox; Library keeps remote Notes and Account
keeps identity/workspace settings. Opening Activity requests an authoritative
inbox refresh. Its busy guard prevents entry reads from canceling/replaying an
active mutation. The explicit Refresh action remains available for delayed
delivery. Navigation never inserts a notice, marks it read or switches accounts.
Notes live hints do not become an Inbox subscription.

The existing backend Files use case commits its completion fact transactionally.
Notifications consumes that fact through the existing delivery seam, evaluates
the recipient's current media preference and records its accepted/suppressed
decision. An upload receipt establishes producer completion, not consumer delivery
or current file existence. Native UI never synthesizes delivery from that receipt.
Media preferences govern future decisions; enabling them does not resurrect a
suppressed decision. Read state and media preferences still require readback
after uncertain acknowledgement and have no command receipts/CAS.

Files retry continues to read the original completion receipt before PUT. Inbox
refresh only reads current state. Automatic entry/foreground reads cannot replay
either owner's mutation. Process restoration reloads session/server pages and
the separate durable Files intent; retry remains explicit. It does not establish
a unified offline queue. Background transfers, system push, richer notice kinds
and retention remain separate.

CLI 0.26.0 adds [scoped file destinations](notice-destinations.md). Open file uses
a separate read-only owner and current authorized metadata, with an explicit
verified download preview. Mark read remains independent. Every exact-lease
rebind closes the destination; neither feature's mutation/recovery owner changes.

The copied media acceptance begins with an empty account and a real gallery
fixture. Actual screens upload three photos, settle one lost completion reply
through its receipt, read the first backend-created notice, reconcile lost
read/preference replies, mute the second delivery, restore while muted and enable
the third without resurrecting suppressed notices. Both features are checked
across account switches, renewal, process restoration/recreation and sign-out.
The fault proxy verifies distinct original upload commands, one binary PUT and
one completion attempt each, completion receipt lookup, one mark-read attempt
and exactly the explicit false/true preference writes. The full scenario also
runs the base connected and separate Files/Inbox screen workflows.
The current media workflow additionally opens a notice into a verified file,
then opens a target removed by an independent authorized client. The latter
must show the actual backend 404 without changing unread count.

Candidate inventories become ready only after independent generated native
execution against both selected backend processes, with no selected skips or
failures. Read [construction and evidence](../../docs/blueprints/connected-media.md).

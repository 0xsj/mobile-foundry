# Inbox projection and read identity

Claim: read and archive state belong to stable update identities, while filters,
counts, groups and detail presentation are separate projections of those values.

Origin/evidence, 2026-10-09: the eighteenth UI batch builds the same bounded
inbox fixture in SwiftUI and Compose. Source and native checks exercise read-on-open,
filtered command scope, archive/undo, disabled admission and retained identities.
This is an in-app presentation pattern; push delivery and synchronized receipts
remain outside this slice.

## What and why

An unread filter changes which updates are visible. Marking a visible update read
may remove it from that projection, but should not erase its identity or close
an already-open detail merely because it no longer matches the filter. Detail
availability depends on active identity and action eligibility, not visible index.

Unread count describes active updates across the inbox. A visible count describes
the current filter. Supply each meaning explicitly. A compact `99+` count needs
a complete accessible label, such as `128 unread updates`; the component cannot
infer the meaning of a caller-formatted string. Zero visibility, cap, plurals,
relative times and ordering all belong to the feature/localization layer.

Open, mark-read and archive are independent commands. Put native sibling actions
outside the open target and recheck enabled/active identity in feature commands.
Unread is conveyed with text and narration as well as visual emphasis. It is
not the same as selected, disabled, or OS notification authorization.

## Example and gotchas

Archive Review and Invitation, then mark the visible unread Tools update read.
Undo restores Invitation at its original fixture position, still unread. Review
stays archived: this fixture stores one latest archive intent, not a batch undo
stack. Archiving preserves the record's read state. Undo removes that archive
identity; it does not rewind other read actions or apply to a new visible index.

Opening Invitation marks it read and keeps details available even though it
leaves Unread. Archiving that identity or disabling actions closes the sheet.
Restoration keeps read/archive/filter/opened values but discards sheet presentation.
The fixture uses explicit array order and supplied Today/Earlier labels; it does
not calculate date boundaries or schedule time-label refreshes.

## Actual use and next questions

Read [Swift inbox](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#notifications-gallery),
[Kotlin inbox](../../frontend/kotlin/notes/modules/project/app/README.md#notifications-gallery),
[UI usage](../../docs/blueprints/ui-components.md#notifications-and-inbox)
and [behavior](../../contracts/behavior/ui-components.md#notifications-and-inbox).
Related: [selection identity and undo](selection-identity-and-undo.md),
[search projections](search-projection-and-filter-drafts.md) and
[account scope](account-context-and-device-capabilities.md).
Next: decide per-account receipt scope, offline command admission, concurrent
archive/read merges and undo conflict policy before connecting a real inbox service.
Native checks do not establish full assistive traversal or device performance.

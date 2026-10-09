# Selection identity and undo

Claim: filtering, selection, removal and undo operate on different projections,
so stable record identity and feature-owned admission must connect them.

## Origin and evidence

Added 2026-10-09 for the tenth UI batch. The local library fixture exercises
selection through filtering, archive/removal and one latest undo batch. Native
walkthroughs link source and executed checks. No persistence, file deletion or
remote write is implemented by this example.

## What and why

A visible row position changes when the filter changes or an earlier record is
removed. Selecting by position can silently transfer intent to another record.
Keep a set of stable IDs above conditional views, derive visible records from the
source, and compute hidden selection from selected IDs absent from that projection.
The aggregate checkbox changes visible IDs only. Bulk commands may affect all
selected IDs, so the example explicitly displays the hidden count.

SelectionRow renders one controlled choice. SwipeActionRow supplies native row
actions and transient gesture presentation. Neither owns records or undo. The
feature guards disabled, missing and already removed IDs even when UI controls
also gate admission. A swipe position is not durable command intent: restoring
a dismissed position must not automatically replay a removal.

Example: select Orbit and Field, then filter to Orbit. The summary shows two
selected and one hidden. Archive selected changes both IDs. Remove selected
captures their prior selection, removes both from the projection and clears their
selection. Undo restores the same IDs and captured selection; archive flags remain,
and original ordering comes from the unchanged source sequence.

The example retains only the latest removal batch. A second removal replaces the
undo snapshot; an empty/stale callback does not. Dismissing the notice discards
that recovery intent. A real store needs a policy for concurrent changes: undo
may fail, a record may have changed, or server-side removal may be irreversible.
Use existing command/result seams when introducing those operations.

TokenField similarly projects an external draft and token list. The library
feature trims one label, rejects case-insensitive duplicates and caps tags at six.
Rejection preserves the draft; accepted admission clears it. Those are feature
rules, not universal parsing rules in a text control. A recipient picker, hashtag
field and structured filter can reuse the same slots with different admission.

## Gotchas and alternatives

- Immutable fixture labels can identify tags; editable product labels need stable
  IDs. Keep identity separate from display copy and localization.
- Use native row actions in their supported host. Swift List supplies swipe
  behavior; a ScrollView cannot acquire it just by wrapping a card.
- Small token groups can measure eagerly and wrap. Large lists need lazy layout,
  bounded records and separate scroll ownership.
- Visible menus make row commands discoverable and provide a gesture alternative.
  Their callbacks should use the same admission as swipe actions.
- Saved-instance values recover a small nonsecret fixture; they are not a durable
  collection cache. Swift view state alone does not survive process termination.

## Used in and related

Read [behavior](../../contracts/behavior/ui-components.md#selection-tokens-and-row-editing),
[usage](../../docs/blueprints/ui-components.md#selection-tokens-and-row-editing),
[Swift native mechanics](../../frontend/swift/notes/substrate/swiftui-wrapping-and-list-actions.md)
and [Compose native mechanics](../../frontend/kotlin/notes/substrate/compose-wrapping-and-swipe-actions.md).
Compare [collection projections](collection-projections-and-feedback-lifetime.md)
and [caller-owned slots](component-slots-and-caller-owned-state.md).
Next: what receipt or revision should a persistent library's undo capture?

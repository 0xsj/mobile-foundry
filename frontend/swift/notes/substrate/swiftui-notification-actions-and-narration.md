# SwiftUI notification actions and narration

Claim: native open buttons can expose complete supplied meaning while independent
sibling controls retain their own actions and accessibility elements.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; UI package minimum iOS 17/macOS 14. Apple's
[accessibilityElement documentation](https://developer.apple.com/documentation/swiftui/view/accessibilityelement(children:))
describes transforming child accessibility behavior. The component's choice of
combined open copy and independent sibling actions is repository policy.

## Mechanism and example

NotificationRow stores its generic ViewBuilder slots and supplied copy as values.
Its native Button contains passive artwork and a vertically growing copy column.
`.combine` plus a complete accessibility label preserves native action behavior
while reporting title/message/time/read meaning. Artwork is hidden; actions live
outside the Button. The row's enabled flag applies only to opening. Hosts supply
independent eligibility to action slots rather than disabling the whole subtree.

```swift
// Conceptual: the feature supplies localized read/time meaning and admits commands.
NotificationRow(title, message: message, timeLabel: timeCopy, stateLabel: readCopy,
                isUnread: unread, accessibilityLabel: completeCopy, onOpen: open) {
    Image(systemName: "bell")
} actions: {
    ActionButton("Mark read", enabled: canMarkRead, action: markRead)
}
```

CountBadge is passive native Text in a token capsule. It replaces only its own
passive text semantics with complete caller narration; it neither parses the
visible string nor adds an action. Semantic fonts and unconstrained vertical copy
grow at larger text sizes; HStack leading placement follows layout direction.

## Evidence, gotchas and actual use

[Native cases](../../apps/FoundryCatalog/Tests/NotificationComponentTests.swift)
exercise read/archive command scope and actual 240-point hosted layout growth,
44-point independent action bounds and logical artwork placement in RTL.
Both consumers build; 51 iOS app checks and seven Swift UI package checks pass.
There is no full VoiceOver, all-locales or physical-device audit in this slice.

The caller must include every meaningful state/time detail in the supplied label;
hidden artwork cannot carry unique information or interactive controls. Do not
merge the whole row and its independent actions into one element. A view disabled
state does not replace feature command admission. Opening marks read only because
the catalog explicitly chooses that policy. Its sheet flag is transient @State,
separate from retained opened identity; filter disappearance does not dismiss it.

Read [UI walkthrough](../modules/packages/FoundryUI/README.md#notifications-and-inbox),
[consumer flow](../modules/apps/FoundryCatalog/README.md#notifications-gallery) and
[shared identity pattern](../../../../notes/patterns/inbox-projection-and-read-identity.md).
Next: exercise VoiceOver traversal and localized labels, then connect read/archive
commands through the existing feature service seam.

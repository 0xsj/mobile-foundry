# SwiftUI tree actions and indentation

Claim: separate native buttons let tree opening and disclosure retain independent
eligibility while capped logical indentation preserves readable bounds.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple's
[View accessibility documentation](https://developer.apple.com/documentation/SwiftUI/View-Accessibility)
describes labels, values and modifiers for native views. This implementation
keeps the Button's role, supplies its full label/selected trait, and supplies
expanded/collapsed value separately on the disclosure IconAction.

## Mechanism and example

TreeRow uses a native Button for opening and a sibling IconAction for disclosure.
The optional TreeDisclosure is caller state plus a callback, not a recursive
model. Leading artwork is passive and hidden from the row's narration; the
supplied full label must include its meaning. Independent actions live beneath
the row rather than inside its opening Button.

```swift
// Conceptual: flattened entry values and eligibility belong to the feature.
TreeRow(title, subtitle: detail, accessibilityLabel: fullLabel, depth: depth,
        selected: selected, enabled: canOpen, disclosure: branch,
        onOpen: open, leading: {
    FileTypeMark("PNG", accessibilityLabel: "PNG image")
}, actions: { ActionButton("Favorite", variant: .quiet, action: favorite) })
```

Leading padding is the minimum of depth times the step and the maximum. Defaults
are 16 points and a 48-point cap; inputs must be nonnegative and finite. Native
leading alignment/padding follows layout direction. Accessibility Dynamic Type
moves artwork above title/subtitle to preserve room for vertically growing copy.
FileTypeMark uses a semantic caption and intrinsic-width content rather than a
fixed clipped image or inferred extension.

## Evidence, gotchas and actual use

[Hosted/owner cases](../../apps/FoundryCatalog/Tests/FileComponentTests.swift)
exercise fixture projection/admission and 240-point hosted geometry at extreme
depth, accessibility text sizes and RTL, including bounded mark/action placement.
Both consumers build; 55 iOS app checks and seven Swift UI package checks pass.
This does not establish full VoiceOver traversal or an automated iOS inspector
interaction. Android separately exercises its actual native sheet and restoration.

Opening's enabled flag does not automatically disable disclosure or slot actions;
the feature supplies each eligibility. Passive artwork cannot contain interactive
controls. Native buttons combine children and retain roles; only the passive mark
ignores child narration. The app's inspector flag is transient @State and closes
when selected-item eligibility becomes false. Selected identity stays in the owner.

Read [UI walkthrough](../modules/packages/FoundryUI/README.md#files-and-hierarchy),
[consumer](../modules/apps/FoundryCatalog/README.md#files-gallery) and
[shared projection pattern](../../../../notes/patterns/tree-projection-and-retained-selection.md).
Next: localized hierarchy narration, VoiceOver traversal and loaded-child provider
admission outside the reusable row.

# SwiftUI member slots and selectable links

Claim: passive identity and independently accessible slots let a member row expose
role controls without nesting them inside a whole-row action.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; UI package minimum iOS 17/macOS 14. Apple's
[textSelection documentation](https://developer.apple.com/documentation/swiftui/view/textselection(_:))
describes native text selection; on iOS 26 and earlier its context menu operates
on the whole Text value. Apple's
[pasteboard string documentation](https://developer.apple.com/documentation/uikit/uipasteboard/string)
describes replacing the pasteboard's current items when assigning a string.

## Mechanism and example

MemberRow stores ViewBuilder avatar/access/action values. Only the passive
identity/decoration group ignores child narration and receives the supplied full
identity label. Access and action controls remain separate native elements.
Accessibility Dynamic Type stacks avatar above identity; copy grows vertically.

```swift
// Conceptual: role choices and command admission belong to the feature.
MemberRow(name, detail: address, accessibilityLabel: identityCopy,
          avatar: { avatarView }, access: { rolePicker }, actions: {
    ActionButton(removeCopy, variant: .destructive, enabled: canRemove, action: requestRemoval)
})
```

ShareLinkCard renders a nonnil string as monospaced native Text with selection
enabled. Nil renders caller-supplied unavailable copy. Header/detail/status/actions
are outside that text-selection scope. It does not use a native Link or infer a
destination; URL admission and explicit copy stay in the feature.

The app's guarded copy returns an admitted example string and increments a request
count, then invokes its copyText callback. The default callback assigns
UIPasteboard.general.string. No clipboard work occurs while rendering, choosing
a role or changing theme. Selection behavior follows the installed OS, rather
than promising cross-platform range-selection parity.

## Evidence, gotchas and actual use

[Hosted/owner cases](../../apps/FoundryCatalog/Tests/SharingComponentTests.swift)
exercise known/duplicate/unavailable invitations, protected/stale/disabled/pending
commands, explicit copy admission, resets and 240-point accessibility-text/RTL
identity/link growth with minimum native action bounds. Both consumers build;
57 iOS app checks and seven Swift UI package checks pass. Hosted geometry does
not drive the native text-selection menu, clipboard UI or iOS confirmation dialog.

Do not ignore child accessibility on the whole MemberRow: that would hide role
and removal controls. Avatar is passive and must add no unique meaning beyond
the supplied identity. The dialog stores a revision-bound request in transient
@State and closes on membership/eligibility changes. The copy counter records
admission, not evidence of a later paste or remote access.

Read [UI walkthrough](../modules/packages/FoundryUI/README.md#sharing-and-access),
[consumer](../modules/apps/FoundryCatalog/README.md#sharing-gallery) and
[shared membership pattern](../../../../notes/patterns/membership-identity-and-confirmed-revisions.md).
Next: VoiceOver/localized traversal, native selection/clipboard presentation and
service version admission before real membership effects.

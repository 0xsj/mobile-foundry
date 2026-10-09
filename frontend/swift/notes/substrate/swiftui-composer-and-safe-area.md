# SwiftUI composer and safe area

Claim: a binding/focus-driven multiline field and a screen-owned safe-area inset
can keep the draft separate from both transcript scrolling and sending policy.

## Origin and evidence

Added 2026-10-09 for the ninth UI batch. Swift 6.2.3, Xcode/SwiftUI 26.2,
iOS 17 / macOS 14 UI-package minimum. Primary references:
[TextField](https://developer.apple.com/documentation/swiftui/textfield),
[FocusState](https://developer.apple.com/documentation/swiftui/focusstate),
[safeAreaInset](https://developer.apple.com/documentation/swiftui/view/safeareainset(edge:alignment:spacing:content:)-6gwby)
and [ViewThatFits](https://developer.apple.com/documentation/swiftui/viewthatfits).
Apple's TextField Markdown was read alongside the native source. The module
walkthrough links hosted checks and manual simulator observations separately.

## What and why

MessageComposer reuses MultilineField's vertical TextField with a visible line
range of 1...5. The caller supplies Binding<String> and FocusState binding. The
field edits text; it never decides whether the string is sendable. There is no
onSubmit send handler, so native Return remains multiline editing. The feature
passes canSend and onSend, then clears text only after its own admission succeeds.

```swift
// Excerpt: screen-owned placement, outside transcript scrolling.
.safeAreaInset(edge: .bottom, spacing: 0) {
    ConversationComposer(values: $values).padding(12)
}
```

The destination uses ScrollView for transcript and recovery controls. It inserts
the compact composer into the bottom safe area, which reserves layout space and
responds to the native keyboard rather than overlaying controls on transcript
content. Interactive keyboard dismissal is screen policy. No UIKit keyboard
notification observer or fixed keyboard height is introduced.

ViewThatFits chooses a horizontal or vertical composer action arrangement from
available width. Attachments and the input sit outside those alternatives, so
layout selection does not create a second editor. Native inherited disabled state
gates attachment/action slots when the composer is disabled or busy. MessageBubble
keeps native text selection separate from interactive accessories; artwork
semantics are hidden only in explicitly passive slots.

## Gotchas, actual use and limits

The hosted narrow layout check measures longer conversation copy growing at
accessibility3 and verifies that an independent attachment action remains inside
its bubble with native minimum height. App checks exercise failure, pause/retry,
attachment-only admission, cancellation and disabled callbacks. Manual iOS 26.2
execution shows the composer above the visible software keyboard, then executes
attachment failure → retry → complete → send and Back/dark-theme retention.

The simulator initially used its hardware keyboard; software keyboard visibility
was explicitly toggled for observation. One CUA typeText call delivered only part
of its multiline payload; that is not evidence of complete text-input automation.
Android instrumentation and Swift owner checks separately exercise multiline text.
No full VoiceOver, all keyboard/language combinations, macOS runtime, iPad/rotation
or physical-device audit is claimed. Large pinned slots still require host layout
review. Next: how should a real transcript preserve scroll position on a new reply?

## Related

- [UI walkthrough](../modules/packages/FoundryUI/README.md#communication-and-attachments).
- [Communication gallery](../modules/apps/FoundryCatalog/README.md#communication-gallery).
- [Input/focus mechanics](../modules/packages/FoundryUI/README.md#rich-input-and-journey-pages).
- [Shared draft/transfer ownership](../../../../notes/patterns/composer-drafts-and-transfer-ownership.md).

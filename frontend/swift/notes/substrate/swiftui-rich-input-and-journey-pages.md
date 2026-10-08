# Rich input and journey pages in SwiftUI

Claim: native text input, draft ownership and page identity are separate concerns,
so a page can reset its presentation without discarding a hoisted draft.

## Origin and evidence

Added 2026-10-08 for the sixth reusable component batch. Environment: Swift
6.2.3, SwiftUI/Xcode 26.2, iOS 17 minimum and macOS 14 package minimum. Source
inspection establishes binding/focus ownership and layout structure; the native
module walkthroughs record executed checks and their limits.
Primary references: [SecureField](https://developer.apple.com/documentation/swiftui/securefield),
[vertical TextField](https://developer.apple.com/documentation/swiftui/textfield),
[lineLimit](https://developer.apple.com/documentation/swiftui/view/linelimit(_:)-4hzfa)
and [view identity](https://developer.apple.com/documentation/swiftui/view/id(_:)).

## What and why

SecureField hides entered text while writing through a binding. Keyboard submit
and native focus remain explicit. Current/new password content types on iOS
supply autofill metadata; associated domains, actual provider integration and
storage are separate. A masked field still has a caller-owned draft value.
Do not mirror that value into helper labels or generic screen restoration.

A vertical TextField with a positive ClosedRange lineLimit provides a growing
editor with a bounded visible height. Additional text scrolls inside the field;
the line limit is not a character truncation policy. An explicit Done button
can end focus without turning every newline into a command. TextEditor remains
an alternative when a feature needs a document-like editing surface.

```swift
// Conceptual: enclosing view owns both text and focus.
TextField("Introduction", text: $introduction, axis: .vertical)
    .lineLimit(3...6).focused($introductionFocused)
SecureField("Password", text: $password)
    .focused($passwordFocused).onSubmit { passwordFocused = false }
```

Passive requirement/progress rows replace decorative symbol semantics with
supplied title/state copy. These are not checkbox or tab controls. Stable IDs
keep row identity distinct from display labels and from progress status.

An account-form shell can scroll header, form and footer together. An onboarding
page instead gives its scrolling body remaining space and reserves actions
outside it. Changing id for a step recreates page-local scroll state. Important
text/preferences must live above that identity; clearing focus before changing
steps avoids asking a removed field to remain the active editor.

## Gotchas and alternatives

- Visible line count depends on native font metrics; do not use fixed pixel
  heights as a substitute for Dynamic Type.
- A page wrapper should receive bounded height and should not sit in another
  unbounded vertical scroller. Long actions can still exhaust a small viewport.
- View-local State is not durable profile or credential persistence. A real
  feature must choose restoration and admission at its own boundary.
- Checklist satisfaction and step completion are caller projections. Native
  layout cannot establish that a remote command succeeded.

Observed 2026-10-09 on the iOS Simulator with a hardware keyboard: Return ended
editing, while Option-Return inserted a newline and subsequent text. Reopening
the onboarding route retained the full three-line draft. The software keyboard
was not exercised. A clipboard-paste attempt timed out; a direct AX value change
did not establish a binding update, so neither is counted as retained-draft
evidence. Native coordinate focus plus typed text/Option-Return supplied that
evidence. Do not generalize a hardware-keyboard commit into a software-keyboard
newline guarantee.

## Used in and next questions

The UI walkthrough links PasswordField, MultilineField, ValidationChecklist,
StepIndicator, OnboardingPage and AuthShell. The catalog walkthrough traces the
shared profile note and explicit local counters. Next: when does a longer editor
need TextEditor, and how should a real route own a resumable nonsecret draft?

## Related

- [Form focus and submission](swiftui-form-focus-and-submit.md).
- [Bounded detail regions](swiftui-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08).
- [Shared draft/step ownership](../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08).

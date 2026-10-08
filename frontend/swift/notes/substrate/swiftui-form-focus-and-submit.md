# SwiftUI form focus and submission

Native focus is a UI binding; mutation execution belongs to a stable feature
owner whose synchronous admission runs before a task is launched.

## Origin and evidence

The 2026-10-08 forms slice uses Swift 6.2.3, Xcode 26.2 and an iOS 17 baseline.
See the [UI](../modules/packages/FoundryUI/README.md) and
[catalog](../modules/apps/FoundryCatalog/README.md) walkthroughs for source/tests.
Primary references: [TextField](https://developer.apple.com/documentation/swiftui/textfield),
[FocusState](https://developer.apple.com/documentation/swiftui/focusstate), and
[submitLabel](https://developer.apple.com/documentation/swiftui/view/submitlabel(_:)).
Build results establish API compatibility; native interactions and accessibility
state establish only the behaviors actually exercised, not VoiceOver quality.

## What and why

`@FocusState` stores whether the native field owns focus. Its projected value is
`FocusState<Bool>.Binding`, distinct from the ordinary `Binding<String>` used
for text. The wrapper accepts both, so the feature can focus an invalid field
or dismiss the keyboard after admission without the component creating state.

```swift
// Excerpt — inside CreateNoteScreen.body
FoundryTextField("Title",
    text: Binding(get: { store.title }, set: { store.editTitle($0) }),
    error: store.titleError, enabled: store.canEdit, focus: $titleFocused,
    onBlur: { store.blurTitle() }, onSubmit: { submit() })
```

The persistent visible label remains when input is populated. Native TextField
retains editing, selection and keyboard behavior. Help becomes an accessibility
hint; errors have text and an icon as well as critical color. Focus change from
true to false marks blur, avoiding validation on the initial unfocused render.
`submitLabel(.done)` selects keyboard presentation; `.onSubmit` supplies behavior.
Both it and the button call the same feature action.

The view asks the store to submit synchronously. Invalid admission returns nil
and focuses the title; accepted work returns a task handle and clears focus.
The store marks Submitting before creating that task. A disabled widget helps
presentation, but the store guard protects every caller, including keyboard
actions queued before a redraw. The view does not create a Task per tap.
The catalog owns the store through State and calls stop on disappearance.

## Language mechanics behind the command

A private initializer makes CreateNote constructible only through its factory.
Result's failure is the kernel Failure, while protocol `async throws` still
permits cancellation and unexpected defects. The actor memory adapter serializes
access to its array; append contains no await, so no actor reentrancy occurs
between its cancellation check and commit. Swift arrays provide value semantics:
returning a snapshot cannot let a caller mutate the actor's stored array.
These choices do not make cancellation a transactional rollback mechanism.
Unicode scalars supply code-point counting; String.count would count extended
grapheme clusters and disagree with Kotlin's selected limit.

## Gotchas and alternatives

During this slice Swift 6.2.3 crashed in IR generation when the Binding setter
used the main-actor method reference `store.editTitle` directly. An explicit
closure calling the same method compiles. This is a local compiler observation,
not a language rule requiring closures for every method reference. Keep the
workaround local rather than weakening isolation or adding unchecked Sendable.

Only one field exists. Multiple fields would need a typed focus target and
explicit Next/Done ordering. Draft lifetime currently ends with the screen;
durable drafts and process restoration need an explicit storage owner.

Related: [observable stores](../language/swift-observable-stores-and-service-protocols.md),
[async ports](../language/swift-async-ports-and-continuations.md), and
[shared mutation ownership](../../../../notes/patterns/forms-and-mutation-ownership.md).

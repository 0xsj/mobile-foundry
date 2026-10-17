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
LabeledTextField("Title",
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


## Distinct secret fields — 2026-10-17

Claim: an optional enum focus value gives each field a distinct identity when a
form includes current/new passwords and verification codes. Origin: native lifecycle
forms on iOS 26.2/Xcode 26.3. An initial UI run reached the security form but returned
local invalid input instead of sending verification mail; its recording showed
strong-password covers on multiple fields sharing one Boolean focus binding.
Distinct focus identities alone did not resolve the second run. The form now
also separates verification/change modes and uses a privacy-sensitive TextField
for the one-time code rather than treating it as another password. Reruns are tracked in [execution evidence](../../../../docs/blueprints/native-identity-live.md).

Excerpt from the product form (inside its View):

```swift
// Excerpt: fields bind distinct values; nil ends editing before submission.
private enum Field: Hashable { case email, verificationPassword, code, currentPassword, newPassword }
@FocusState private var editing: Field?
```

The enum prevents programmatic focus from ambiguously selecting several fields.
Text content types remain appropriate for native credential/OTP assistance; a
custom drawing of a password field would discard those system behaviors. Secret
values still clear on submit/background/dismissal. Do not infer mail failure from
a form test that never admitted a valid request.

Related: [actual form](../../../../templates/overlays/go-connected/swift/IdentitySettingsView.swift).
Next: test relevant AutoFill behavior on a signed device before making release UX claims.

## Separate registration drafts — 2026-10-17

Claim: a registration password can remain a transient native draft without being forwarded into sign-in or saved state. Origin: connected registration form on the existing toolchains; [actual device evidence](../../../../docs/blueprints/native-registration.md) distinguishes exercised UI from lifecycle source inspection. Read [form](../../../../templates/overlays/go-connected/swift/RegistrationView.swift).

The separate sheet uses typed FocusState targets and username/name/newPassword content types. Submission copies the current secret into the one operation and immediately clears the field. scenePhase, epoch changes and onDisappear clear draft/focus and invalidate model work. Continue transfers only login. Native tests retain system password assistance and handle the observed strong-password prompt.

Widget disabling is not the concurrency guard: the owner synchronously admits one attempt and blocks resubmission after success until a new form is opened. A server-created identity can outlive a dismissed form. Next: verify credential-provider and accessibility behavior on target signed devices before claiming release-quality onboarding.


## Workspace field replacement — 2026-10-17

Claim: an XCTest text edit must account for the actual insertion point, not just
the current string length. Origin: the iPhone 17 Pro/iOS 26.2 workspace workflow.
The first helper tapped the middle of an existing name and sent enough backward
deletes for the full value. The observed result retained a suffix (`iOSiOS`), so
the owned fault proxy correctly did not recognize the intended conflict fixture.
This was test input, not accepted evidence for the conflict branch.

The corrected fixture helper puts the caret at the field's trailing edge before
backward deletion, then asserts exact editable text after typing. The selected
short fixture names fit the field; this is not a general clear-text API for
arbitrary overflowing text or every language/selection mode. Production forms
still use native editing and caller-owned drafts. Follow
[the actual UI checks](../../../../templates/overlays/go-connected/swift/ConnectedUITests.swift)
and [final execution evidence](../../../../docs/blueprints/native-workspaces.md).
Toolbar controls are tapped directly while visible. Applying the generic scroll
reveal helper to Done could pull an already-at-top sheet down and dismiss it;
the subsequent lookup correctly found no close button. That is native sheet
behavior, so the test must distinguish scrolling content from using its toolbar.
Next: which product fields need overflow/selection/paste accessibility checks
beyond these short acceptance fixtures?

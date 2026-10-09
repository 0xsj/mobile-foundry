# Compose composer and IME

Claim: hoisted TextFieldState preserves native draft editing while screen-owned
IME padding and flexible transcript space keep presentation out of UI-core policy.

## Origin and evidence

Added 2026-10-09 for the ninth UI batch. Kotlin 2.3.20, AGP 9.0.1,
Compose BOM 2026.03.01, Material3 1.4.0, JDK 17, minSdk 24 / compileSdk 36.
Primary references: [state-based text input](https://developer.android.com/develop/ui/compose/text/user-input),
[inset padding and consumption](https://developer.android.com/develop/ui/compose/system/insets-ui)
and [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).
Source explains composition; module notes link executed checks and their limits.

## What and why

MessageComposer receives a caller TextFieldState, then reuses MultilineField with
TextFieldLineLimits.MultiLine through a 1..5 range. Native state owns text,
selection and composition; no async onValueChange callback is inserted between
IME editing and state. The app uses rememberTextFieldState above route replacement
for its nonsecret fixture draft. Its primitive flags/lists restore separately;
the CommunicationValues snapshot itself is not assumed saveable.

```kotlin
// Excerpt: the slot's independent control consumes composer interactivity.
actions = { interactive ->
    IconAction("Add preview attachment", { onChange(values.addAttachment()) },
        enabled = interactive && !values.attached) { Text("+") }
}
```

Compose does not inherit a universal disabled flag into arbitrary content lambdas.
The composer gates native field/send itself and passes enabled && !isSending into
slots. Consumers must use that argument on their native actions. FlowRow wraps
actions at narrow widths without creating another TextFieldState. No send-on-Return
action handler is added; message admission/clearing remains with the feature.

The destination uses a bounded Column with imePadding, a weighted scrolling
transcript/recovery region and a separate compact composer. Native inset modifiers
consume applied insets and adapt layout with IME changes. Core/ui does not choose
edge-to-edge, focus, system-bar or navigation policies. Back clears focus and
returns through the app's route owner.

## Gotchas, actual use and limits

Instrumentation exercises multiline input, pending-file send blocking, failure,
retry, pause/resume, completion, independent inspection, draft clearing, cancellation,
disabled controls and saved-instance restoration after Back/family/theme changes.
A narrow font-scale-two composition checks passive artwork/typing semantics,
independent action activation and busy input/slot/send gating without clearing text.

Disabled native fields remove SetText actions, so selectors must use retained
labels rather than expecting editing actions on a disabled node. A pinned action
has no scrolling ancestor; performScrollTo is only valid for actual scrolling
content. The final empty-draft assertion targets EditableText separately from
label/help text. Initial harness failures and corrections are recorded in the
app walkthrough. No complete TalkBack, hardware keyboard, IME animation/occlusion,
rotation, device performance or real upload audit is claimed. Next: which inset
owner should a production shell share with its nested conversation route?

## Related

- [UI walkthrough](../modules/project/core/ui/README.md#communication-and-attachments).
- [Communication gallery](../modules/project/app/README.md#communication-gallery).
- [Rich input](../modules/project/core/ui/README.md#rich-input-and-journey-pages).
- [Shared draft/transfer ownership](../../../../notes/patterns/composer-drafts-and-transfer-ownership.md).

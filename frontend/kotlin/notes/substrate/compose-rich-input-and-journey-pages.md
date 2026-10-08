# Rich input and journey pages in Compose

Claim: hoisting native TextFieldState preserves editing state, while its owner's
remember/save policy independently determines whether a draft survives restoration.

## Origin and evidence

Added 2026-10-08 for the sixth UI batch. Environment: Kotlin 2.3.20, Compose BOM
2026.03.01, Material3 1.4.0, Android API 36 emulator, minimum API 24. Builds and
actual native interaction checks are recorded in the module walkthroughs.
Primary references: [state-based fields](https://developer.android.com/develop/ui/compose/text/user-input),
[native secure field](https://developer.android.com/reference/kotlin/androidx/compose/material3/OutlinedSecureTextField.composable),
[autofill metadata](https://developer.android.com/develop/ui/compose/text/autofill)
and [Compose identity](https://developer.android.com/develop/ui/compose/lifecycle#add-info-smart-recomposition).

## What and why

TextFieldState carries text, selection and composition. Pass the caller-owned
object to a state-based field; read text for readiness and use edit methods for
programmatic changes. Recreating it from a String every composition discards
native editing state. rememberTextFieldState includes save/restore support;
plain remember { TextFieldState() } does not deliberately save the draft.

PasswordField uses OutlinedSecureTextField with hidden text and current/new
ContentType metadata. The wrapper contains its Material experimental opt-in.
This preserves the native secure editing path rather than manually applying
PasswordVisualTransformation to an ordinary field. Submission remains an
optional native KeyboardActionHandler. No session or storage is created.

```kotlin
// Conceptual: inside a feature's composition, above conditional page content.
val password = remember { TextFieldState() }
val introduction = rememberTextFieldState()
OutlinedSecureTextField(state = password, textObfuscationMode = TextObfuscationMode.Hidden)
OutlinedTextField(state = introduction,
    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6))
```

Visible line limits bound the viewport rather than truncating the draft. Error
copy replaces help; native error semantics remain on the input. Existing
LabeledTextField retains its value/callback API; this slice does not migrate it.
A callback-based alternative is simpler when only String projection matters,
but the new secure field uses the native state-based API.

Validation/progress rows merge supplied copy with a state description and hide
symbols from accessibility. Progress status text is visible but removed from
child semantics to avoid reading it twice. Neither row type has a click action.

OnboardingPage reserves actions outside its scrolling body; AuthShell scrolls
its footer with the form. A key around one step resets page-local scrolling
while drafts stay above the keyed subtree. The catalog clears focus before
changing that key. Routing remains in the application.

## Gotchas and actual observations

A disabled secure field has no SetText action in the tested native semantics.
A test that combined its label with that action could not locate the disabled
node. Use the visible label plus a disabled assertion; do not infer editability
from label presence. The final module walkthrough records corrected execution.

Saved nonsecret text/progress are examples of saved-instance restoration, not
an account database. Do not accidentally use a saveable helper for every input
merely because the same helper is convenient for profile text. A real feature
still needs keyboard/inset, provider-autofill and process/storage decisions.

## Used in and related

Read the UI walkthrough for the native wrappers and the catalog walkthrough for
shared note state, local actions and restoration checks. Next: which route needs
an entry-owned ViewModel, and how should remote errors join a local checklist?

- [Form focus and submission](compose-form-focus-and-ime.md).
- [Bounded detail regions](compose-layout-and-contextual-presentation.md#disclosure-and-bounded-detail-regions--2026-10-08).
- [Shared draft/step ownership](../../../../notes/patterns/component-slots-and-caller-owned-state.md#input-drafts-and-journey-steps--2026-10-08).

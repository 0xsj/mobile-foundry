# Compose code entry and autofill hints

Claim: a native text field with a code content-type hint keeps input mechanics
separate from submission, challenge lifetime and service verification.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17, SDK 36/min 24, API 36 emulator. Android's
[Compose Autofill guidance](https://developer.android.com/develop/ui/compose/text/autofill)
describes setting contentType semantics on native text fields. The
[ContentType reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/autofill/ContentType#SmsOtpCode())
provides SmsOtpCode. The hint configures purpose; suggestions depend on the
installed OS/provider and are not established by a semantic assertion.

## Mechanism and example

OneTimeCodeField uses a controlled OutlinedTextField with Number/Done keyboard
options, SmsOtpCode semantics and explicit error/supporting copy. CodeFormat
builds a canonical String from ASCII digits, allowing only the five specified
separator characters. Invalid/oversized edits return null and change nothing;
an empty result clears. Leading zeroes remain text rather than a numeric value.

```kotlin
// Conceptual: the feature owns the challenge, draft and submit command.
OneTimeCodeField("Verification code", draft, onDraft, format = CodeFormat(6),
    help = "Enter six digits.", error = errorCopy, enabled = canEdit,
    canSubmit = canVerify, onSubmit = verify)
```

Done checks enabled, caller canSubmit and complete digits. Editing/autofill never
submits automatically. TextDirection.Ltr keeps code order while surrounding layout
remains logical RTL. VerificationCard clears semantics only on passive delivery
identity/artwork; native content/status/action slots remain outside that group.
At font scale at least 1.5 the artwork moves above growing copy.

Codes and attempt/error/result use transient remember; nonsecret channel/response,
generation, times, enabled state and counters use rememberSaveable above route
returns. Recreation discards a pending request/code rather than resuming a check.
Route/theme changes retain them in memory because the owner stays composed.

## Evidence, gotchas and actual use

[Format checks](../../project/core/ui/src/test/kotlin/dev/mobilefoundry/ui/CodeFormatTest.kt)
exercise canonical admission, empty/partial codes, leading zeroes, unsupported
lengths, Unicode rejection and overflow.
[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/VerificationComponentTest.kt)
drive native text replacement/Done, assert the autofill hint, reject invalid
input and preserve independent actions, plus narrow large-text/RTL bounds.
[Gallery checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/VerificationCatalogTest.kt)
cover local challenge commands, recreation and earlier cart retention.
Native Autofill suggestions, physical paste menus, full TalkBack and real auth
are separate observations.

Disabled native text fields remove their SetText action from the semantic tree.
Find their stable label when asserting disabled state instead of requiring an edit
action. Hint semantics do not perform SMS retrieval or request message permission.
The preview's manually remaining time is not a durable deadline or authorization.

Read [UI walkthrough](../modules/project/core/ui/README.md#verification-and-code-entry),
[consumer](../modules/project/app/README.md#verification-gallery) and
[shared challenge pattern](../../../../notes/patterns/challenge-drafts-and-attempt-identity.md).
Next: real provider challenge identity/deadline and native Autofill observations.

# SwiftUI code entry and content hints

Claim: one native editable field preserves code-entry behavior while a content
hint describes purpose and the caller retains explicit submission policy.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; UI package minimum iOS 17/macOS 14. Apple's
[oneTimeCode documentation](https://developer.apple.com/documentation/uikit/uitextcontenttype/onetimecode)
defines the input purpose; its
[native AutoFill guidance](https://developer.apple.com/documentation/security/enabling-password-autofill-on-a-text-input-view)
describes native content types and cautions that a custom keyboard prevents the
security-code suggestion UI. The repository uses the native keyboard.

## Mechanism and example

OneTimeCodeField supplies a controlled TextField Binding. Its setter admits only
the CodeFormat result when enabled; nil rejects, while an empty string clears.
CodeFormat iterates Unicode scalar values so only ASCII digits and the explicitly
allowed ASCII separators are accepted. It preserves leading zeroes as a String.
The caller value must already be canonical partial digits, and length is 1...12.

```swift
// Conceptual: challenge, draft, focus and submission belong to the feature.
OneTimeCodeField("Verification code", text: $draft, format: CodeFormat(length: 6),
    help: "Enter six digits.", error: errorCopy, enabled: canEdit,
    canSubmit: canVerify, focus: $focused, onSubmit: verify)
```

On iOS the field sets oneTimeCode, numberPad and no capitalization; macOS uses the
native field without the iOS-specific hint. Semantic monospaced type grows with
Dynamic Type. Only input digit layout is left-to-right; surrounding label/help
follow the host. Done checks enabled, completeness and canSubmit. The iOS number
pad has no standard Return key, so the example always supplies a visible Verify
button; a content hint or a complete edit never triggers submission.

VerificationCard ignores child narration only in passive delivery identity/artwork
and supplies full heading copy. Content/status/actions remain native siblings.
Accessibility Dynamic Type stacks passive artwork above growing title/destination.

## Evidence, gotchas and actual use

[Hosted/owner checks](../../apps/FoundryCatalog/Tests/VerificationComponentTests.swift)
inspect the hosted UITextField's oneTimeCode/numberPad configuration, 240-point
large-text/RTL growth, native action bounds and local attempt/expiry admission.
[Format checks](../../packages/FoundryUI/Tests/FoundryUITests/CodeFormatTests.swift)
exercise separators, clear/partial codes, leading zeroes, overflow and Unicode
rejection. These checks do not drive an iOS paste/keyboard/AutoFill suggestion,
full VoiceOver or real delivery/authentication.

Do not split entry into per-digit focus fields or auto-submit a completed paste.
The field's admission says nothing about whether a service accepts the code.
Keep the entire card out of an ignored accessibility group, or its input and
verification actions would disappear. Pending results belong to feature identity,
not a native text field's current visible value.

Read [UI walkthrough](../modules/packages/FoundryUI/README.md#verification-and-code-entry),
[consumer](../modules/apps/FoundryCatalog/README.md#verification-gallery) and
[shared challenge pattern](../../../../notes/patterns/challenge-drafts-and-attempt-identity.md).
Next: native paste/AutoFill/VoiceOver observations and service challenge outcomes.

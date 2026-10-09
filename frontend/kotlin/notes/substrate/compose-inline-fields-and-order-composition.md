# Compose inline fields and order composition

Claim: hoisted text and native button/keyboard admission let adaptive input/action
layouts change without owning validation, pricing or a request.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17; compile/target SDK 36, min SDK 24. Google's
[text-field guide](https://developer.android.com/develop/ui/compose/text/user-input)
documents hoisted input and keyboard options. The
[KeyboardActions reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/KeyboardActions)
describes software keyboard actions. The breakpoint and guarded callback are
repository choices rather than framework guarantees.

## Mechanism and example

InlineActionField uses one value-based OutlinedTextField and one ActionButton.
The onValueChange callback synchronously updates owner values; submit callbacks
share enabled/not busy/canSubmit gating. The control keeps Done as its keyboard
action while copying caller input options such as capitalization. Native error
semantics stay on the field; help/error copy below the adaptive controls chooses
error first. Eligibility=false alone disables submission while leaving edits open.

```kotlin
// Conceptual: text, validation and effect lifetime belong to the host.
InlineActionField("Invite code", draft, onDraft, "Use code", canUse, useCode,
    isBusy = working, error = feedback)
```

BoxWithConstraints reads the supplied local width. Below 400 dp or fontScale >=
1.5 it stacks; otherwise a Row centers the action beside a weighted field.
Text/draft ownership is outside those layout branches. The existing library's
value/callback API keeps this small field consistent with its synchronous form
consumers. Google's guide recommends state-based TextFieldState for richer input
state ownership; use that existing approach when composition/selection or
transformations require it, rather than adding async work inside onValueChange.

PriceLabel clears passive child semantics and supplies complete narration.
ProductRow clears only decorative artwork, leaving status and independent native
actions intact. OrderSummary has native heading/divider, supplied lines/total and
an independent footer; no monetary calculation occurs inside it.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommerceComponentTest.kt)
exercise button/Done eligibility, retained text, busy/disabled input, passive price
meaning, action bounds and 240-dp larger-text/RTL growth. Changing width/text scale
preserves the controlled draft; cursor/focus under every resize is not established.
[Consumer checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/CommerceCatalogTest.kt)
exercise totals, explicit code application, quantity limits, route/theme changes,
saved-state restoration and discarded review sheets. Four focused cases pass on
the isolated API 36 emulator, alongside six UI package contract cases.

Native disabled fields remove editing semantics; locate them by their label to
check availability. Clear price children only when all meaningful copy is in its
supplied label, and keep product action semantics independent. These checks do
not establish TalkBack, localized currency arithmetic or a physical-device audit.
Read [UI source](../modules/project/core/ui/README.md#products-and-order-composition),
[consumer flow](../modules/project/app/README.md#commerce-gallery) and
[price/command ownership](../../../../notes/patterns/price-copy-and-committed-cart-values.md).
Next: test native assistive traversal/localized widths and connect actual quotes
and mutations outside these reusable compositions.

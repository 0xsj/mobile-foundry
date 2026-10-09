# Compose plan slots and usage bars

Claim: a native selected choice action can remain independent of plan slots,
while usage bars project supplied fractions without owning entitlement state.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, JDK 17; compile/target SDK 36, min SDK 24. Google's
[progress guide](https://developer.android.com/develop/ui/compose/components/progress)
documents a determinate progress lambda and a separate indeterminate overload.
The missing/nonfinite usage-bar policy here is explicit repository behavior.

## Mechanism and example

PlanCard composes an opaque Card with native price/status/feature slots and one
ActionButton. Its supplied selected state is attached to the native action via
semantics; selected or disabled choices reject dispatch. The outer card is not
clickable, so independent help/status actions are neither nested targets nor
implicitly disabled with the choice button.

```kotlin
// Conceptual: selection, product meaning and commands belong to the host.
PlanCard("Studio", selected, choiceCopy, choose, enabled = canChoose,
    price = { PriceLabel(priceCopy, fullPriceCopy) }, features = {
        FeatureRow("Offline drafts", "Included", true, "Studio, offline drafts included")
    })
```

FeatureRow and UsageMeter replace only passive child semantics with complete
supplied narration. The meter sanitizes a supplied Float to a finite clamped
fraction and uses LinearProgressIndicator's progress lambda; null/nonfinite
omits it. It does not display a loading animation, parse copy or grant capability.
Weighted native copy grows vertically and uses logical layout direction.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlanComponentTest.kt)
cover passive narration, native selected/disabled states, independent slot actions
and 240-dp font-scale-two/RTL growth with minimum choice/action bounds.
[Consumer checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/PlanCatalogTest.kt)
exercise unchanged draft entitlement, review/apply, retained usage, exceeded
allowance, pending/disabled/stale guards, unavailable options, theme/family/route
changes, saved-state recreation and retained earlier cart state. Four focused
cases pass on the isolated API 36 emulator; six UI package cases pass.

Native review presence uses remember and an eligibility-keyed LaunchedEffect
for dismissal; nonsecret choice/current/usage/review primitives are saveable above
route branches. Recreation discards presentation, retains metadata and applies
nothing. The new preview branch comes after earlier family owners. Passive slots
cannot carry missing meaning, and selected state is not entitlement authorization.
No TalkBack, billing service or physical-device audit is established.
Read [UI walkthrough](../modules/project/core/ui/README.md#plans-and-usage),
[consumer](../modules/project/app/README.md#plans-gallery) and
[shared ownership](../../../../notes/patterns/plan-choice-and-applied-allowance.md).
Next: exercise assistive/localized layouts and admit real products/receipts
through feature service adapters without moving those policies into the UI.

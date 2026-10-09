# SwiftUI plan slots and usage bars

Claim: native slots and an explicit choice button let plan content remain
independently accessible while a passive meter receives complete usage meaning.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; UI package minimum iOS 17/macOS 14. Apple's
[ProgressView documentation](https://developer.apple.com/documentation/swiftui/progressview)
describes determinate values and an indeterminate initializer without a value.
This repository explicitly omits the bar for absent/nonfinite usage rather than
choosing the native indeterminate initializer.

## Mechanism and example

PlanCard stores native ViewBuilder price/status/feature values inside an opaque
Card. Its ActionButton has selected traits and rejects selected/disabled choices.
The card itself has no tap action; independent slot controls keep their own
targets. The host distinguishes selected from current and supplies its choice
label, rather than inferring entitlement from the selected trait.

```swift
// Conceptual: the host supplies meaning and selection admission.
PlanCard("Studio", selected: selected, actionLabel: choiceCopy, enabled: canChoose,
         onSelect: choose, price: { priceView }, status: { statusView }, features: {
    FeatureRow("Offline drafts", stateLabel: "Included", included: true,
               accessibilityLabel: "Studio, offline drafts included")
})
```

FeatureRow keeps logical leading artwork decorative and makes its complete
supplied label one passive element. UsageMeter similarly replaces passive
value/bar/detail meaning with full narration. Finite fractions clamp visually;
overflow meaning must remain in supplied copy. Its native semantic fonts grow
vertically; no percentage or quota is calculated from a formatted value string.

## Evidence, gotchas and actual use

[Hosted/owner cases](../../apps/FoundryCatalog/Tests/PlanComponentTests.swift)
exercise draft versus current allowance, stale/pending/disabled review admission,
retained usage, exhausted/exceeded state, 240-point large-text growth, readable
price/feature/usage bounds, slot action targets and logical RTL slot placement.
Both consumers build; 53 iOS app checks and seven Swift UI package checks pass.
This does not establish full VoiceOver traversal or an iOS purchase interaction.

Do not make the entire Card one accessibility element or button when its slots
contain independent actions. Passive labels must include every meaningful detail;
decorative marks/bars cannot carry unique information. The app's transient @State
review flag closes on invalidated eligibility, while reviewed values stay retained.
No effect is performed merely by choosing a tier or changing theme.
Read [UI walkthrough](../modules/packages/FoundryUI/README.md#plans-and-usage),
[consumer](../modules/apps/FoundryCatalog/README.md#plans-gallery) and
[shared ownership](../../../../notes/patterns/plan-choice-and-applied-allowance.md).
Next: exercise VoiceOver/localized copy and introduce service quote/receipt
identity before a real entitlement application.

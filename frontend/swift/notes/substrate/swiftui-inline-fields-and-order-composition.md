# SwiftUI inline fields and order composition

Claim: native layout alternatives can align input and action without moving
draft ownership or command eligibility into the component.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3 and iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple's
[ViewThatFits documentation](https://developer.apple.com/documentation/swiftui/viewthatfits)
describes choosing the first child whose ideal size fits the constrained axes.
Its [TextField documentation](https://developer.apple.com/documentation/swiftui/textfield)
documents native input and onSubmit. Admission/layout policy here is local.

## Mechanism and example

InlineActionField places a rounded native TextField and ActionButton inside
FieldGroup. Shared help/error sits below the controls rather than contributing
to their alignment. TextField binds the owner's text/focus and has a Done action.
The same submit function gates button and keyboard on enabled, not busy and
canSubmit. The component neither clears text nor changes focus on success;
the catalog callback explicitly clears focus before applying its local code.

```swift
// Conceptual: the host owns the draft, focus and effect admission.
InlineActionField("Invite code", text: $draft, actionLabel: "Use code", canSubmit: canUse,
                  isBusy: working, error: feedback, focus: $focused, onSubmit: useCode)
```

At accessibility text sizes it stacks controls. Otherwise ViewThatFits evaluates
a horizontal row with a 180-point minimum field and intrinsic action width,
then a stacked fallback. Only horizontal fit is constrained; text can grow
vertically. Generic ProductRow/OrderSummary builders store native slot values;
product artwork is hidden while price, status and actions retain their meaning.
PriceLabel replaces its passive children with complete supplied narration.

## Evidence, gotchas and actual use

[Native checks](../../apps/FoundryCatalog/Tests/CommerceComponentTests.swift) cover
fixture quantity/code/disabled/busy admission, totals, retained review snapshots
and actual 240-point hosted bounds. Larger text grows product/field content,
action targets stay inside their regions, artwork follows RTL and draft text
remains retained. These are geometry/state observations, not every keyboard
gesture or VoiceOver traversal. The macOS UI package compiles and its seven
contract cases pass; the iOS catalog has 49 passing app cases.

ViewThatFits can change which view branch is present; keep draft and service
owners above it. These checks preserve text, not cursor/focus across every resize.
Parent onSubmit behavior and keyboard dismissal remain host policy. Product
artwork must stay passive. Do not merge independent order/footer actions into
one accessible price summary or infer previous-price meaning from styling alone.

Read [UI source](../modules/packages/FoundryUI/README.md#products-and-order-composition),
[consumer flow](../modules/apps/FoundryCatalog/README.md#commerce-gallery) and
[price/command ownership](../../../../notes/patterns/price-copy-and-committed-cart-values.md).
Next: exercise VoiceOver and localized labels/amounts, then connect real quotes
through a feature service without placing monetary calculation in these views.

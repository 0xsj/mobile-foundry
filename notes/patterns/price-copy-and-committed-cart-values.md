# Price copy and committed cart values

Claim: reusable price presentation should receive meaning from its owner, while
draft codes, applied discounts, current totals and review snapshots remain distinct.

Origin/evidence, 2026-10-09: the Commerce UI batch implements a bounded local cart
on both native platforms. Native consumer checks exercise quantity admission,
explicit code application, retained choices and review snapshots. This is a UI
fixture rather than a monetary domain, quote service or purchase flow.

## What and why

A price string is display copy. It does not establish currency arithmetic,
discount eligibility or an authoritative quote. Supply current, comparison and
unit meaning explicitly; a strike-through alone is insufficient accessible copy.
Keep formatting/calculation separate from a reusable row or summary composition.
Native action slots let products, bookings and subscriptions supply their own
quantity and eligibility controls without embedding those policies in the UI.

An edited promotional code is a draft. Applying it is an admitted command;
merely typing another value should not silently replace an existing discount.
Button and keyboard submission must share that same eligibility, including busy
state. Validation feedback belongs to the owner and can coexist with a previously
applied valid discount. Clearing draft, removing applied discount and emptying
quantities are separate operations.

Current totals derive from admitted quantities and committed choices. A recorded
review snapshot describes the values at one moment, so later edits should not
rewrite it. Restore persistent feature values separately from transient review
presentation. A production server quote needs its own identity, validity and
admission rules before reservations or purchases can occur.

## Example and gotchas

Three Studio kits and one Pocket notebook cost $99 in this fixed USD fixture.
Pick up costs zero; applying STUDIO10 makes the total $89.10. Editing an invalid
draft preserves that applied discount. Clearing quantities gives zero total but
retains delivery/code/discount. Restoring one kit gives $26.10 with Pick up.
A previously recorded $89.10 review remains that snapshot after these changes.

The example's bounded integer cents avoid floating-point drift, but they do not
provide a general money type. Currency scale, tax, mixed-currency arithmetic,
rounding, pricing freshness and inventory reservation need domain decisions.
The fixture prices happen to divide exactly for the sample ten-percent discount;
do not generalize its division to other currencies or policies. Disabled UI is
not server authorization. No payment or purchase effect is performed here.

## Actual use and next questions

Read [Swift Commerce flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#commerce-gallery),
[Kotlin Commerce flow](../../frontend/kotlin/notes/modules/project/app/README.md#commerce-gallery)
and [component usage](../../docs/blueprints/ui-components.md#products-and-order-composition).
Related: [forms and mutation ownership](forms-and-mutation-ownership.md) and
[search projections and drafts](search-projection-and-filter-drafts.md).
Next: admit actual server quotes through a service seam, specify a monetary
contract and connect mutation state before implementing any purchase operation.
Native checks do not establish a full accessibility, localization or device audit.

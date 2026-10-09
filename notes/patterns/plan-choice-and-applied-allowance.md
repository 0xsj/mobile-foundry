# Plan choice and applied allowance

Claim: selected plan, reviewed command, applied allowance and current usage are
distinct values; changing presentation or a draft must not grant capability.

Origin/evidence, 2026-10-09: the nineteenth UI batch builds a local plan picker
with three reusable components on SwiftUI and Compose. Native source/checks
exercise selection, review admission, application and quota preservation. Its
fixed prices/allowances are bounded fixtures, not store products or billing rules.

## What and why

Selection describes what the user is considering. Applied plan describes what
the feature currently projects as active. A review identifies a specific choice
and billing cycle. Application rechecks that choice and current eligibility;
changing the draft invalidates an outstanding review. Disabled or pending UI
alone is not command admission, so the owner guards the same conditions again.

Feature and price strings are supplied meaning. A check mark cannot establish
availability without readable copy; a monthly-equivalent price cannot describe
an annual charge alone. Keep billing cadence and actual billed amount explicit.
Reusable plan cards project those native slots rather than load products or
interpret prices. An explicit choice button lets help/status controls stay
independent instead of nesting them inside a whole-card button.

Usage belongs to its own account/period scope. Choosing another tier or billing
cycle does not reset it. A lower applied allowance may therefore be exceeded.
The visible bar can clamp at full while supplied text/narration reports 50 of 5
and exceeded meaning. Nil/nonfinite quota fractions omit the decorative bar;
unlimited/unknown copy belongs to the caller, not an indeterminate loading spinner.

## Example and gotchas

Start with three exports on Starter's five-export allowance. Select Studio/Yearly:
the price reads $6 per month with $72 billed yearly, but current usage remains
three of five. Review and Apply preview change switches allowance to fifty while
retaining three used. Reach fifty, then apply Starter. Usage stays fifty, the
bar stays full and simulated export remains blocked. Reset usage is explicit.

The fixture's review matches plan/cycle values; it does not model a server quote
version, receipt transaction or expiration. Recreating Android state retains the
review metadata but discards sheet presence and does not apply anything. Reopening
review requires another explicit action. A real billing flow needs product/quote
identity, lifecycle and admitted service results before applying entitlement.
No renewal timer, purchase verification or offline capability policy is provided.

## Actual use and next questions

Read [Swift Plans flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#plans-gallery),
[Kotlin Plans flow](../../frontend/kotlin/notes/modules/project/app/README.md#plans-gallery),
[usage](../../docs/blueprints/ui-components.md#plans-and-usage) and
[behavior](../../contracts/behavior/ui-components.md#plans-and-usage).
Related: [price and committed values](price-copy-and-committed-cart-values.md),
[account context](account-context-and-device-capabilities.md) and
[forms/mutation ownership](forms-and-mutation-ownership.md).
Next: define actual receipt/entitlement and usage-period scope, then connect a
service adapter. Native checks do not establish full assistive traversal or
physical-device performance.

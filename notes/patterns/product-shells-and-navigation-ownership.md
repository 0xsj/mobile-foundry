# Product shells and navigation ownership

Claim: reusable shell layouts become useful product compositions when navigation,
drafts and effects have explicit owners above changing presentation branches.

Origin: product shell variations, 2026-10-10. Source inspection follows
[the contract](../../contracts/behavior/product-shells.md) and
[construction map](../../docs/blueprints/product-shells.md). Native execution
evidence lives in the [Swift](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#product-shell-variations)
and [Kotlin](../../frontend/kotlin/notes/modules/project/app/README.md#product-shell-variations)
walkthroughs. The fixtures describe behavior rather than native widget structure.

## What and why

Five profiles cover different product shapes rather than different colors of a
single dashboard. Daily has independent native tabs; Workspace selects a project
in one or two panes; Browse returns from detail to filtered results; Canvas keeps
tools and persistent actions around an editable preview; Setup guides a draft
through back, review and explicit completion. These are examples for adaptation.

AppShell supplies bounded backdrop/content/chrome regions. DetailShell reserves
header/actions while its body scrolls. AuthShell constrains readable form content.
SplitPane changes composition using local bounds and native large-text policy.
TabBar admits supplied identities and selection. The app chooses their content,
navigation intent and lifetime, and uses the same semantic tokens for both themes.

The gallery's value owner is above every preview branch. It retains known tab/item
identities, per-page counters, saved/completed IDs, canvas controls and setup
progress. Native input ownership is also above those branches: Swift bindings
edit the gallery value; Kotlin owns TextFieldState directly. Back to the chooser
changes presentation only; closing the whole gallery ends those fixtures.

## Example and gotchas

Select Research, complete it, return to the list and open Release. Research stays
completed. Expand the window and both panes appear, retaining the selected project.
Switch the material or leave/reopen the preview: those presentation changes
retain the feature's selected item and editable draft.

Increments read the current owner for each callback. A captured old snapshot can
lose rapid repeated clicks. Only known IDs are admitted; layer count saturates
at eight. Finishing Setup requires Start again to clear name, focus and progress
without resetting other profiles.

Theme choice, selected detail intent and feature data are different decisions.
A local detail branch does not supply native swipe-back history, deep links,
predictive-back animation, fold posture or navigation-stack restoration. A product
requiring those capabilities should use an appropriate native navigation owner.
Saving a collection and Preview export affect local fixture values; neither
claims real disk/network/export behavior. Saveable Android values are small
UI state, not a place for images, service instances or durable records.

## Actual use and next questions

The compositions remain in app-owned Shell/Patterns directories. Shared scenarios
exercise profile independence, retained item actions, unknown IDs, layer bounds
and guided reset. Native checks separately exercise tabs, width adaptation,
back, inputs and selected recreation behavior. The UI count remains 112/30.
These examples establish candidate shapes; independent generation remains the
[starter readiness](../../docs/STARTER-READINESS.md) gate.

Related: [shell chrome](shell-chrome-and-feature-lifetime.md),
[adaptive state](adaptive-layout-and-navigation-state.md),
[starter verification](../techniques/standalone-starter-verification.md).
Next: which profile should be the first independently generated product, and
which examples should be optional rather than dependencies of that output?

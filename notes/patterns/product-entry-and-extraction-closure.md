# Product entry and extraction closure

Claim: a small product entry should own identity, navigation and screens while
an extraction inventory owns the selected module/test closure; neither belongs
inside the kitchen-sink catalog or reusable components.

## Origin and evidence

Implemented 2026-10-10 after the five product shell examples. The clean Daily
entries have separate Swift/Kotlin application builds and four placeholder
screens. They import UI → Query → Kernel; the gallery's Notes, session, camera
and graphics owners are not required. Two hosted iOS checks and two real Android
Activity checks exercise the product boundary. See the native walkthroughs for
precise evidence and limits.

## What and why

The catalog answers “what can this capability do?” A product entry answers
“where do I put my app's first screen and configuration?” Copying the gallery
would also copy sample authority selection, simulated receipts, camera permissions,
assets and unrelated product state. Shared layouts need no new router abstraction
to support the clean entry: native tab identity and themed surfaces already have
the required seams.

The first extraction profile deliberately selects UI only. Its transitive
Query/Kernel code remains necessary because the UI module exports query-state
presentation. Source selection therefore follows declared dependencies, not
which controls happen to be on today's screen. Native tests need their fixtures
even when the installed app never packages those fixtures.

## Example

Selecting Swift ProductApp requires FoundryUI, FoundryQuery and FoundryKernel.
Selecting Kotlin `:product` requires `:core:ui`, `:core:query` and `:core:kernel`,
plus wrapper/version/build files. The product-only settings overlay registers
that closure. Canonical fixture directories retain their relative depth.
Branding changes occur in product files; component names and library namespaces
are preserved.

## Gotchas and limits

An inventory is not a generator. File existence and closed dependencies do not
establish exclusion enforcement, renamed identity, safe destination handling,
failure cleanup, independent launch or a usable feature-development workflow.
The current checker accepts the source inventory and rejects deliberately removed
Query dependencies, missing UI fixtures and an escaping relative source path.
Those checks are static evidence. Native entry execution remains evidence from
this checkout, so readiness stays at foundation candidate.

Build identity and source module identity are different inputs. Keep the
Swift module/scheme and Kotlin R namespace stable unless intentionally renaming
source; change installed product identity in product configuration. No credential
namespace is created for this UI-only profile. A session capability must add that
input explicitly rather than reuse a catalog namespace.

## Actual use and related reading

See [profile ownership](../../templates/README.md),
[Swift ProductApp](../../frontend/swift/notes/modules/apps/ProductApp/README.md),
[Kotlin product](../../frontend/kotlin/notes/modules/project/product/README.md),
[entry behavior](../../contracts/behavior/product-entry.md),
[shell ownership](product-shells-and-navigation-ownership.md) and
[standalone verification](../techniques/standalone-starter-verification.md).
Next: how should CLI selection validate identities, apply overlays and prove that
generated output never depends on the source checkout?

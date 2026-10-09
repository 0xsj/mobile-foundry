# Tree projection and retained selection

Claim: expanded identities, search projection and selected identity are separate
values; changing what is visible need not change what the user last opened.

Origin/evidence, 2026-10-09: the twentieth native UI batch builds a local Files
preview over eight fixed, acyclic records. Source and native checks exercise
ancestor-aware projection, independent commands and retained selection. These
are fixture identities rather than filesystem paths or provider handles.

## What and why

A reusable row receives depth, selected state, supplied copy and callbacks. It
does not fetch children, recurse through a graph or own expansion. The feature
turns its hierarchy into ordered visible rows and can later replace its data
adapter while keeping that presentation seam.

The fixture projects depth first in stable sibling order. With no query it uses
saved expansion IDs. A trimmed search includes matching titles plus their
ancestors so a deeply nested file has understandable context. Search reveals
those paths without changing saved expansion; manual disclosure is disabled
until the query clears. Matching a folder alone does not include unmatched
descendants. This is an explicit product policy, not a property of all trees.

Selection and favorites use identities rather than row indices. Collapsing a
folder, searching another title or entering an empty scenario retains those
values. Opening requires a known, available, visible identity and active feature.
An already selected item can remain inspectable or favorited while hidden by a
filter. Empty/disabled state closes the inspector, whose presence is transient.

## Example and gotchas

Collapse all folders, then search `field`. Atlas, Studies and References appear
above Field image.jpg; Field notes.md appears at root. Open and favorite the
image, then clear search. The browser returns to its saved collapsed view while
the last-opened identity and favorite remain. Reset browser restores initial
expansion and clears query/selection; it retains favorites and open count.

Indentation caps at a supplied maximum so a deep hierarchy retains readable
bounds. The row still needs depth/path meaning in supplied narration; capped
visual position cannot represent every level. Opening, disclosure and favorite
are separate native controls. Never nest a favorite button inside a whole-row
open button. File-format artwork is passive; its meaning belongs in the full row
label too.

This eight-record traversal assumes its fixture parents are acyclic. Real
provider trees need identity scope, cycle handling, child loading, paging,
permissions and reconciliation outside the row. No rename, import, file decoding,
OS access or persistence service is implemented in this example.

## Actual use and next questions

Read [Swift Files flow](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#files-gallery),
[Kotlin Files flow](../../frontend/kotlin/notes/modules/project/app/README.md#files-gallery),
[usage](../../docs/blueprints/ui-components.md#files-and-hierarchy) and
[behavior](../../contracts/behavior/ui-components.md#files-and-hierarchy).
Related: [search projection](search-projection-and-filter-drafts.md),
[selection identity](selection-identity-and-undo.md) and
[adaptive navigation state](adaptive-layout-and-navigation-state.md).
Next: decide provider identity and loaded-child scope before connecting a service;
exercise localized labels and full assistive traversal separately from these
native command/layout checks.

# Table sorting and page ownership

Claim: a table can preserve aligned native presentation while sorting, paging
and row-action admission remain feature policies.

Origin/evidence, 2026-10-09: the Tables batch, source inspection and native
consumer checks. Read the [contract](../../contracts/behavior/ui-components.md#tables-and-pagination),
[Swift walkthrough](../../frontend/swift/notes/modules/apps/FoundryCatalog/README.md#tables-gallery)
and [Kotlin walkthrough](../../frontend/kotlin/notes/modules/project/app/README.md#tables-gallery)
for completed checks and limits. The Project ledger is a nine-record fixture.

## What and why

Sort the admitted collection before projecting a page. A table component should
not sort only its displayed rows and imply that those rows are the first records
in the full ordering. Give equal primary values a deterministic tie-breaker so
page membership does not depend on incidental input order. A server must apply
the same ordering and pagination rules if it owns the full collection.

Page state is also a policy: changing the sort can preserve the current page or
return to the beginning. Make that choice in the feature. A native sort header
emits an action, while PaginationBar emits a valid adjacent page. Neither owns
the request, page size, cursor, total or response admission.

## Example

Sort Sessions ascending, then open page 2. Field, Lumen and Atlas are the fourth
through sixth records of the full ordering. Inspect Field to retain its stable
ID. Empty records hides the projection and disables page controls, preserving
page 2 and that ID. Turning it off restores page 2. Disabling actions prevents
sort/page/inspection without discarding their values.

## Gotchas and actual use

The fixture uses raw English string ordering and an ascending ID tie-breaker in
either primary direction. That is a deliberate example policy, not localized
production collation. Server paging additionally needs a consistent snapshot or
cursor policy; the current local page cannot establish that guarantee.

DataTable eagerly renders a small supplied page. Column widths include padding
and one horizontal scroller preserves header/cell alignment. The host owns
vertical scrolling and contextual cell narration, including independent native
actions. No virtualization, sticky header, frozen column or native grid header
association is promised. Use native tables/lists for richer or dense data.

Related: [caller-owned components](component-slots-and-caller-owned-state.md),
[selection identity](selection-identity-and-undo.md),
[Swift substrate](../../frontend/swift/notes/substrate/swiftui-table-columns-and-scrolling.md),
[Compose substrate](../../frontend/kotlin/notes/substrate/compose-table-columns-and-scrolling.md).
Next: should a real service return numbered pages or cursors, and which sort
revision must a late response match before replacing visible records?

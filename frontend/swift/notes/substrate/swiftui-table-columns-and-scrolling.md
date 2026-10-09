# SwiftUI table columns and scrolling

Claim: fixed column frames inside one horizontal ScrollView can keep a small
eager page aligned while native copy grows vertically.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator; package minimum iOS 17/macOS 14. Apple's
[ScrollView documentation](https://developer.apple.com/documentation/swiftui/scrollview)
describes native scroll axes and the visible content region. Fixed column widths,
small-page rendering and feature ownership are repository decisions.

## Mechanism and example

DataTable's generic Row is Identifiable. Stable ForEach row/column IDs preserve
identity when a supplied ordering changes. Header/cell ViewBuilder closures may
contain native controls. HStack rows use the same column widths; cell padding is
inside each frame. One horizontal ScrollView moves the whole table, and the host
provides vertical scrolling. A row height grows to fit its largest native cell.

```swift
// Conceptual: the feature supplies contextual copy and native cell controls.
DataTable("Records", rows: visibleRows, columns: columns, header: { column in
    Text(column.label)
}, cell: { row, column in
    Text(cellCopy(row, column)).accessibilityLabel(contextualCopy(row, column))
})
```

TableSortHeader delegates native action semantics to ActionButton and receives
explicit TableSortOrder/current-state narration. The helper was renamed after
the iOS consumer compile exposed Foundation.SortOrder ambiguity. Prefixing it
with Table describes its scope without obscuring native Foundation APIs.
The column descriptor is DataTableColumn because SwiftUI already provides a
native [TableColumn](https://developer.apple.com/documentation/swiftui/tablecolumn).
Its name preserves that native API for clients importing both modules.
PaginationBar uses the existing WrapLayout so native actions retain minimum
target bounds as supplied copy grows.

## Evidence, gotchas and actual use

[Native checks](../../apps/FoundryCatalog/Tests/TableComponentTests.swift) compare
actual 240-point header/cell frames, larger-text height and RTL order. The hosted
probe also drives public UIScrollView contentOffset on the nested horizontal
container; it establishes movement/alignment, not a finger gesture or stable
SwiftUI implementation detail. Its first lookup stopped at the outer vertical
scroll view; recursing through scroll-view descendants corrected that harness.

The table keeps independent cell controls rather than combining an interactive
row into one accessible label. This does not provide native Table header
associations or establish a full VoiceOver/keyboard audit. Keep supplied columns
wide enough for real copy, and admit bounded pages rather than large collections.
Read [UI APIs](../modules/packages/FoundryUI/README.md#tables-and-pagination),
[gallery](../modules/apps/FoundryCatalog/README.md#tables-gallery) and
[shared policy](../../../../notes/patterns/table-sorting-and-page-ownership.md).
Next: evaluate native Table/List or a virtualized layout when actual row counts
or desktop keyboard requirements justify them.

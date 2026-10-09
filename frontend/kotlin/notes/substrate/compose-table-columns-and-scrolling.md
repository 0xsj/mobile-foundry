# Compose table columns and scrolling

Claim: one native horizontalScroll can align eager rows while hoisted sorting
and page values remain separate from presentation state.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, API 36.1 test emulator, minSdk 24. Google's
[scroll modifier guide](https://developer.android.com/develop/ui/compose/touch-input/scroll/scroll-modifiers)
describes horizontalScroll and ScrollState, and recommends lazy containers for
large lists. Column widths, sorting and page policy here are repository choices.

## Mechanism and example

DataTable receives rowKey, unique columns and composable header/cell closures.
Explicit key scopes preserve row/cell identity when the caller reorders records.
Rows share a summed column width inside one horizontalScroll. Each column Box
applies width before inner padding; Row aligns native content vertically without
merging cell actions. The caller can hoist ScrollState, while an outer native
vertical scroller owns the whole screen.

```kotlin
// Conceptual: presentation scroll state is separate from record/page state.
val horizontal = rememberScrollState()
DataTable("Records", pageRows, columns, { it.id }, Modifier.fillMaxWidth(),
    scrollState = horizontal,
    header = { Text(it.label) },
    cell = { row, column -> Text(contextualCopy(row, column)) })
```

TableSortHeader uses native disabled action semantics and a localized
stateDescription. Its arrow is decorative. PaginationBar wraps native action
targets and projects first/last/global disabled states without changing a page.
The app saves primitive sort/page/inspection values above route/theme changes;
no service or pending request is saved by a component.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/TableComponentTest.kt)
exercise sort state, endpoint/global disabled guards, horizontal scrolling to an
independent cell action, alignment, 240-dp larger text and RTL. The first movement
assertion used a clipped offscreen boundsInRoot rectangle; clipped bounds do not
report the underlying translation. The corrected check observes native ScrollState
movement, visible action activation and aligned header/cell bounds.
[Catalog checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/TableCatalogTest.kt)
cover actual sort/page/inspection, empty projection and saved-state/theme/route
retention. No native grid header association, full TalkBack/keyboard audit,
dense-data performance or server ordering guarantee is established.

Read [UI APIs](../modules/project/core/ui/README.md#tables-and-pagination),
[gallery](../modules/project/app/README.md#tables-gallery) and
[shared policy](../../../../notes/patterns/table-sorting-and-page-ownership.md).
Next: compare a lazy/native record layout for a real data source and define
response admission against its sort/page revision.

# SwiftUI bounded panes and navigation

Claim: local GeometryReader bounds can choose a two-pane composition while
feature values and native route ownership remain above its branches.

Origin/evidence, 2026-10-09: Swift 6.2.3, Xcode 26.2, macOS 26.3, iOS 26.2
simulator, minimum iOS 17/macOS 14. Apple documents
[GeometryReader](https://developer.apple.com/documentation/swiftui/geometryreader)
as a flexible container with local size/coordinate information, and
[NavigationSplitView](https://developer.apple.com/documentation/swiftui/navigationsplitview)
as native multi-column navigation that collapses into a stack at narrow sizes.
SplitPane's width threshold and larger-text policy are repository choices.

## Mechanism and example

SplitPane stores two generic ViewBuilder closures taking PaneMode. GeometryReader
evaluates its local width; the active branch creates native HStack or a single
slot. The host owns bounded height, scrolling and compact detail intent. Native
HStack places the primary slot at the logical leading edge in RTL.

```swift
// Conceptual: this owner survives either pane branch.
@State private var selectedID: String?
@State private var showDetail = false
SplitPane(detailPresented: showDetail, primary: { mode in
    ProjectList(selection: $selectedID)
}, detail: { mode in
    ProjectDetail(id: selectedID, onBack: { showDetail = false })
})
```

The native NavigationSplitView is a useful alternative when a screen owns a
multi-column navigation hierarchy. This catalog preview already sits inside an
owned NavigationStack; its bounded local layout deliberately owns no second
stack, toolbar or routing semantics. Native accessibility Dynamic Type sizes
choose a single pane. DestinationRail uses native Buttons, visible captions,
selected traits and a bounded scroller; BreadcrumbTrail uses ancestor buttons
and passive current copy in WrapLayout.

## Evidence, gotchas and actual use

[Hosted checks](../../apps/FoundryCatalog/Tests/WorkspaceComponentTests.swift)
observe actual 820/280-point pane geometry, RTL, accessibility-size collapse and
feature guards. These synthetic bounds are not a physical iPad observation.
The macOS UI package check compiles generic native APIs; runtime walkthroughs
are recorded in the app note. Keep drafts/services above conditional slots:
ViewBuilder branches do not promise to retain local State from a removed view.

Read [UI APIs](../modules/packages/FoundryUI/README.md#adaptive-workspaces),
[gallery](../modules/apps/FoundryCatalog/README.md#workspace-gallery) and
[shared ownership](../../../../notes/patterns/adaptive-layout-and-navigation-state.md).
Next: observe actual iPad resizing, keyboard focus and native navigation-stack
restoration before adopting this pattern in a routed editor.

## Product shell compositions

Product shells, 2026-10-10, uses the same toolchain and SplitPane API for an
app-owned workspace. The preview fills a bounded AppShell viewport, with a
280-point primary and a 420-point detail/gutter budget. It expands at 700 points;
accessibility Dynamic Type still chooses one pane. DetailShell reserves a fixed
header/footer around a scrolling body. Native TabBar owns Daily's bottom safe
area; the host does not add another inset around it.

ProductShellCatalogView's @State owns all profile values above NavigationLink
previews. Child Bindings mutate that owner; Solid/Glass material changes keep the
content identity. Setup/canvas input uses existing native focus bindings and
clears focus before advancing/resetting. Browse returns to the same filter value.
SwiftUI's keyboard safe area adjusts available layout; this slice does not own a
second keyboard-height notification system.

The preview's Back/Glass controls occupy a top safeAreaInset on the product
content. That native inset reserves measured chrome space before laying out
the shell. A sibling flexible VStack alone let DetailShell consumers squeeze or
overlap the row. An initial fixed-size row improved Workspace/Setup, but visual
capture still exposed Canvas overlap; the measured native inset is the final
composition. The header belongs to the app host, not shared DetailShell.

The local compact back button changes detail intent. A native NavigationSplitView
or per-destination NavigationStack is the alternative when the product requires
native navigation history, deep links or interactive detail back gestures. The
present example deliberately uses bounded composition inside the catalog route.

[The hosted product checks](../../apps/FoundryCatalog/Tests/ProductShellTests.swift)
inspect Daily's actual UITabBar labels/selection and the workspace's native scroll
containers at compact, expanded and accessibility-size bounds, retaining selection
and completion. They also mount/capture all five actual compositions. Geometry
on a hosted simulator is separate from real iPad resizing, VoiceOver or a complete
iOS touch/keyboard journey. See [the app evidence](../modules/apps/FoundryCatalog/README.md#product-shell-variations)
and [shared reasoning](../../../../notes/patterns/product-shells-and-navigation-ownership.md).
Next: select a composition for independent extraction, then choose its real
native navigation/restoration policy rather than copying the gallery owner.

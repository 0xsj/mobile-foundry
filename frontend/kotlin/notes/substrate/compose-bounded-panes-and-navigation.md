# Compose bounded panes and navigation

Claim: BoxWithConstraints can choose local pane content while hoisted selection
and compact back intent outlive composition changes.

Origin/evidence, 2026-10-09: Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0, API 36.1 emulator, minSdk 24. Google's
[adaptive sizing guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes)
distinguishes global window decisions from a nested component's local constraints
and describes the extra layout-phase composition cost of BoxWithConstraints.
The [navigation rail guide](https://developer.android.com/develop/ui/compose/components/navigation-rail)
documents native selected/callback/icon/label inputs. Thresholds, saved values
and back policy in this slice are repository decisions.

## Mechanism and example

SplitPane checks bounded constraints before comparing maxWidth with supplied
primary/minimum-detail widths and token spacing. Row/Box place logical primary
and detail slots. fontScale >= 1.5 or forceSingle selects one slot. The actual
PaneMode is passed to both builders so the host chooses rail or compact controls.

```kotlin
// Conceptual: primitives are saved above the conditional layout branches.
var selectedID by rememberSaveable { mutableStateOf<String?>(null) }
var showDetail by rememberSaveable { mutableStateOf(false) }
SplitPane(showDetail, Modifier.fillMaxSize(),
    primary = { mode -> ProjectList(selectedID) },
    detail = { mode -> ProjectDetail(selectedID, onBack = { showDetail = false }) })
```

DestinationRail wraps native Material NavigationRailItem with passive icons,
supplied labels, enabled/selected semantics and vertical scrolling. Item minimum
height grows with font scale. BreadcrumbTrail wraps native ancestor actions;
current location is passive. Both resolve the existing theme rather than
introducing a routing dependency. BackHandler belongs to the app: the compact
detail handler takes priority over the preview's exit handler.

## Evidence, gotchas and actual use

[Component checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/WorkspaceComponentTest.kt)
exercise native actions, disabled guards, passive current copy, wrapping at
240 dp/font scale 2, RTL and local-width pane branches.
[Catalog checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/WorkspaceCatalogTest.kt)
exercise saved-state, theme/family/route retention and the native Back dispatcher.
A density-adjusted wide test host is logical geometry on a phone emulator,
not observed tablet/foldable behavior. Saving primitives does not persist services,
focus, scroll positions or predictive-back animation. Reflow must not trigger
requests from a removed pane's local remember state.

Read [UI APIs](../modules/project/core/ui/README.md#adaptive-workspaces),
[gallery](../modules/project/app/README.md#workspace-gallery) and
[shared ownership](../../../../notes/patterns/adaptive-layout-and-navigation-state.md).
Next: use a real adaptive navigation scaffold for routed destinations, then
observe tablet/foldable resizing, keyboard traversal and predictive back.

## Product shell compositions

Product shells, 2026-10-10, uses the same pinned native dependencies. Its workspace
consumes SplitPane with 280 dp for the primary and 420 dp including the token gap
for the detail, expanding at 700 dp. The detail closure installs compact
BackHandler only in single-pane mode; Browse and Setup install their own inner
handlers. The outer preview handler returns to the chooser.

ProductShellCatalogScreen retains small fixture values through rememberSaveable
and a JSON String Saver. This uses Bundle-compatible output for an app-owned DTO,
not a general persistence format. IDs, bounded sets and small counters stay in
that value; editable inputs stay in their own rememberTextFieldState above all
preview/theme branches. Each action reads the latest mutable owner, preserving
rapid increments. Start again explicitly edits the native name field once.
There is no deferred effect rewriting input from a mirrored String.

Google documents [saveable state](https://developer.android.com/develop/ui/compose/state-saving)
and [state-based input](https://developer.android.com/develop/ui/compose/text/user-input).
Use minimal saved values; large data belongs in a repository. Our fixture Saver
reads only this app's saved schema, with no external saved-state import/version
migration. Process death and future schema changes are separate verification.

Safe-drawing padding belongs to the catalog route. One preview-level imePadding
shrinks the bounded shell for the keyboard, keeping fixed actions reachable;
scrolling bodies do not apply another keyboard inset. The local two-pane layout
does not implement hinge/posture or predictive-back transitions. Google's
[list/detail navigation scaffold](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)
is an alternative for products needing that native adaptive navigation behavior.

[The product UI checks](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/shell/patterns/ProductShellCatalogTest.kt)
drive the real MainActivity: tabs, native inputs, local actions, back, recreation
and portrait/landscape pane selection. See [the app evidence](../modules/project/app/README.md#product-shell-variations)
and [shared reasoning](../../../../notes/patterns/product-shells-and-navigation-ownership.md).
Next: select a clean generated product entry and verify the chosen navigation
owner on tablet/foldable and physical keyboard hosts.

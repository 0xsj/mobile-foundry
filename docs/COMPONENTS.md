# Native component map

This is a directory sketch for the reusable UI catalog, adapted from Bento's
family/component split. Reserved leaves contain only `.gitkeep`; they are not
implemented APIs or a commitment to wrap every native widget. Remove the marker
when adding real source. Add variants/configuration beside a component only
when a consumer needs them. App routes, stores, repositories and GPU renderers
stay outside this tree.

The roots are:

- [Swift Components](../frontend/swift/packages/FoundryUI/Sources/FoundryUI/Components/)
- [Kotlin components](../frontend/kotlin/project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/)

Swift uses PascalCase folders; Kotlin uses lowercase package folders. These
families stay within the existing UI package/module.

## Implemented controls

| Swift / Kotlin relative folder | Contents |
| --- | --- |
| `Forms/TextField` / `forms/textfield` | Labeled native input, help and error projection. |
| `Forms/SubmitButton` / `forms/submitbutton` | Busy/disabled submit action. |
| `Feedback/Query` / `feedback/query` | Async state rendering and copy. |
| `Feedback/Mutation` / `feedback/mutation` | Write progress, failure and success rendering. |
| `Layout/Surface` / `layout/surface` | Solid/glass surfaces; Kotlin also owns backdrop capture. |
| `Navigation/TabBar` / `navigation/tabbar` | Kotlin: value-driven floating Material bar. Swift leaf is reserved; the app uses native TabView. |

## Reserved leaves

| Swift | Kotlin | Intended contents |
| --- | --- | --- |
| `Forms/Button` | `forms/button` | Primary, secondary, destructive and icon actions. |
| `Forms/Toggle` | `forms/toggle` | Native Boolean switches and labels. |
| `Forms/Checkbox` | `forms/checkbox` | Independent selection with mixed-state support. |
| `Forms/RadioGroup` | `forms/radiogroup` | One choice from a small set. |
| `Forms/Select` | `forms/select` | Native picker and searchable option selection. |
| `Forms/Slider` | `forms/slider` | Bounded numeric input and value labels. |
| `Forms/DatePicker` | `forms/datepicker` | Native date/time selection. |
| `Forms/SearchField` | `forms/searchfield` | Search entry, clearing and keyboard policy. |
| `Forms/FieldGroup` | `forms/fieldgroup` | Related labels, help, validation and field layout. |
| `Display/Avatar` | `display/avatar` | Person or entity image with a fallback. |
| `Display/Badge` | `display/badge` | Compact status and count labels. |
| `Display/Card` | `display/card` | Content composition above a surface. |
| `Display/EmptyState` | `display/emptystate` | Empty-content copy, artwork and caller actions. |
| `Display/ListRow` | `display/listrow` | Leading/trailing accessories and row content. |
| `Display/Stat` | `display/stat` | A labeled value and optional trend. |
| `Layout/Container` | `layout/container` | Page insets and readable content bounds. |
| `Layout/Stack` | `layout/stack` | Consistent vertical/horizontal spacing. |
| `Layout/Grid` | `layout/grid` | Adaptive collections and item spacing. |
| `Layout/Divider` | `layout/divider` | Semantic separators. |
| `Layout/AspectRatio` | `layout/aspectratio` | Media and preview bounds. |
| `Feedback/Alert` | `feedback/alert` | Inline informational, warning and error messages. |
| `Feedback/Progress` | `feedback/progress` | Determinate and indeterminate progress. |
| `Feedback/Skeleton` | `feedback/skeleton` | Reduced-motion-aware loading placeholders. |
| `Feedback/Toast` | `feedback/toast` | Transient caller-owned status messages. |
| `Navigation/Tabs` | `navigation/tabs` | Within-screen segmented destinations. |
| `Navigation/TabBar` | `navigation/tabbar` | Top-level destination items and selected state. Kotlin is implemented in this slice; Swift uses native app chrome. |
| `Navigation/NavLink` | `navigation/navlink` | A labeled navigation affordance; caller owns the route. |
| `Navigation/NavigationRail` | `navigation/navigationrail` | Future larger-screen destination rail. |
| `Overlays/Sheet` | `overlays/sheet` | Native modal/bottom sheet presentation. |
| `Overlays/Dialog` | `overlays/dialog` | Confirmation and destructive-action dialogs. |
| `Overlays/Popover` | `overlays/popover` | Anchored contextual content. |
| `Overlays/Menu` | `overlays/menu` | Native contextual action lists. |
| `Overlays/Tooltip` | `overlays/tooltip` | Short accessible contextual help. |
| `Shells/AppShell` | `shells/appshell` | Reusable layout slots after another app needs the shell. |
| `Shells/AuthShell` | `shells/authshell` | Future sign-in/onboarding layout; no auth implementation. |
| `Shells/DetailShell` | `shells/detailshell` | Detail header, content and action regions. |
| `Patterns/SettingsSection` | `patterns/settingssection` | Grouped settings rows and section copy. |
| `Patterns/PageHeader` | `patterns/pageheader` | Title, supporting copy and actions. |
| `Patterns/SelectionCard` | `patterns/selectioncard` | Selectable content with state and callbacks. |
| `Patterns/CollectionToolbar` | `patterns/collectiontoolbar` | Collection filtering, sorting and selection actions. |

## Ownership

The working four-tab prototype lives in app composition, not the reserved
Shells/AppShell directory. It owns Home, Library, Studio and Account and opens
the existing catalog from Studio. Reusable controls accept values/content and
callbacks; they never choose service adapters or create feature state owners.
See [shell behavior](../contracts/behavior/app-shell.md),
[construction](blueprints/app-shell.md), and [Styles](../STYLES.md).

Native graphics adapters remain in FoundryGraphics/core/graphics. Image studio,
Product studio and Compositor studio are app examples, not UI components.
Charts and workspace editors can receive dedicated families when there is a
concrete native slice to explore.


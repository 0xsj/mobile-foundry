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
| `Forms/Button` / `forms/button` | ActionButton: primary, secondary, quiet, destructive, busy/disabled; Swift also exposes ActionButtonStyle. |
| `Forms/SearchField` / `forms/searchfield` | SearchField: controlled search and explicit clearing. |
| `Display/Badge` / `display/badge` | Badge with neutral/info/warning/critical MessageTone. |
| `Display/Card` / `display/card` | Card with content slots, padding and content/floating surface roles. |
| `Display/ListRow` / `display/listrow` | ListRow with leading/trailing accessories and adaptive copy. |
| `Display/EmptyState` / `display/emptystate` | EmptyState with artwork and action slots. |
| `Feedback/Alert` / `feedback/alert` | InlineAlert with message tone and caller actions. |
| `Feedback/Progress` / `feedback/progress` | Labeled native ProgressIndicator. |
| `Navigation/Tabs` / `navigation/tabs` | Controlled Tabs with native segmented/menu or scrollable presentation. |
| `Patterns/PageHeader` / `patterns/pageheader` | PageHeader with copy and action slot. |
| `Patterns/SettingsSection` / `patterns/settingssection` | SettingsSection for native controls, rows and footer copy. |
| `Patterns/SelectionCard` / `patterns/selectioncard` | Single-choice SelectionCard with caller state. |
| `Forms/Toggle` / `forms/toggle` | ToggleField: full-row native switch and optional help. |
| `Forms/Checkbox` / `forms/checkbox` | Checkbox / CheckState: off, on and mixed with caller-owned transitions. |
| `Forms/RadioGroup` / `forms/radiogroup` | RadioGroup: labeled single-choice options. |
| `Forms/Select` / `forms/select` | SelectField: controlled native menu picker. |
| `Forms/Slider` / `forms/slider` | ValueSlider: continuous or stepped bounded values and caller-formatted copy. |
| `Forms/DatePicker` / `forms/datepicker` | DateField: date-only picker with confirm/discard draft. |
| `Overlays/Sheet` / `overlays/sheet` | SheetPanel / Swift sheetPanel modifier: titled native sheet and content slot. |
| `Overlays/Dialog` / `overlays/dialog` | Swift confirmationPrompt / Kotlin ConfirmationDialog: explicit confirmation. |
| `Overlays/Menu` / `overlays/menu` | ActionMenu / MenuAction: enabled/destructive native actions. |
| `Display/Avatar` / `display/avatar` | Avatar / AvatarShape: fallback or passive artwork, clipped shape and accessible identity. |
| `Display/Stat` / `display/stat` | StatCard: supplied value, units, detail and trend copy. |
| `Feedback/Skeleton` / `feedback/skeleton` | Skeleton / SkeletonShape: decorative loading blocks with reduced-motion-aware pulse. |
| `Feedback/Toast` / `feedback/toast` | ToastBanner / ToastAction: caller-presented message with explicit action/dismissal. |
| `Forms/FieldGroup` / `forms/fieldgroup` | FieldGroup: related native fields with group help/error. |
| `Patterns/CollectionToolbar` / `patterns/collectiontoolbar` | CollectionToolbar: summary, filter and action slots. |
| `Navigation/NavLink` / `navigation/navlink` | NavLink: full-row native navigation affordance and caller route/destination. |
| `Overlays/Popover` / `overlays/popover` | PopoverPanel: anchored caller-controlled contextual content. |
| `Overlays/Tooltip` / `overlays/tooltip` | HelpTooltip: explicitly presented short tap help. |
| `Layout/Container` / `layout/container` | ContentContainer: centered readable bounds including configurable insets. |
| `Layout/Grid` / `layout/grid` | AdaptiveGrid: small eager compositions reflowing with width and text size. |
| `Layout/AspectRatio` / `layout/aspectratio` | MediaFrame: ratio-controlled clipped media slot. |

Component APIs and their helper types have no Foundry prefix. Existing controls
are LabeledTextField, SubmitButton, Surface, Backdrop, TabBar and TabItem.
See [usage examples](blueprints/ui-components.md#using-the-apis) and
[behavior](../contracts/behavior/ui-components.md). Open Studio → Open catalog →
Components for interactive Actions, Content, Patterns, Controls and Overlays
previews, with Display, Feedback, Collections, Context and Layout examples.
Details and removal examples reuse the shared overlay wrappers.

## Reserved leaves

| Swift | Kotlin | Intended contents |
| --- | --- | --- |
| `Layout/Stack` | `layout/stack` | Consistent vertical/horizontal spacing. |
| `Layout/Divider` | `layout/divider` | Semantic separators. |
| `Navigation/TabBar` | `navigation/tabbar` | Top-level destination items and selected state. Kotlin is implemented in this slice; Swift uses native app chrome. |
| `Navigation/NavigationRail` | `navigation/navigationrail` | Future larger-screen destination rail. |
| `Shells/AppShell` | `shells/appshell` | Reusable layout slots after another app needs the shell. |
| `Shells/AuthShell` | `shells/authshell` | Future sign-in/onboarding layout; no auth implementation. |
| `Shells/DetailShell` | `shells/detailshell` | Detail header, content and action regions. |

## Ownership

The working five-tab prototype lives in app composition, not the reserved
Shells/AppShell directory. It owns Home, Library, Camera, Studio and Account and opens
the existing catalog from Studio. Reusable controls accept values/content and
callbacks; they never choose service adapters or create feature state owners.
See [shell behavior](../contracts/behavior/app-shell.md),
[construction](blueprints/app-shell.md), and [Styles](../STYLES.md).

Native graphics adapters remain in FoundryGraphics/core/graphics. Image studio,
Product studio and Compositor studio are app examples, not UI components.
Camera capture and photo admission are also app features. Camera and Image
studio reuse one app-owned photo editor per platform; the GPU preview adapter
still receives only admitted pixels and bounded edit values.
Charts and workspace editors can receive dedicated families when there is a
concrete native slice to explore.

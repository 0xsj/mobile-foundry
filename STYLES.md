# Native styles and components

FoundryUI and core/ui follow [Bento's style organization](../bento/frontend/STYLES.md):
palette primitives, semantic roles, preset mappings, native theme scope, and
components grouped by responsibility. The [token contract](contracts/behavior/ui-tokens.md)
owns the available roles and behavior; this guide owns their placement.

## Directory split

Paths below are relative to `Sources/FoundryUI/` on Swift and
`src/main/kotlin/dev/mobilefoundry/ui/` on Kotlin:

| Responsibility | Swift | Kotlin |
| --- | --- | --- |
| Palette primitives and token families | `Styles/Tokens/` | `styles/tokens/` |
| A complete preset mapping | `Styles/Presets/V1.swift` | `styles/presets/V1.kt` |
| Native theme resolution and scope | `Theme/FoundryTheme.swift` | `theme/FoundryTheme.kt` |
| Async feedback component and copy | `Components/Feedback/Query/` | `components/feedback/query/` |
| Mutation feedback | `Components/Feedback/Mutation/` | `components/feedback/mutation/` |
| Native labeled text input | `Components/Forms/TextField/` | `components/forms/textfield/` |
| Progress-aware submit action | `Components/Forms/SubmitButton/` | `components/forms/submitbutton/` |
| Content and floating surface rendering | `Components/Layout/Surface/` | `components/layout/surface/` |

Tokens are split into Primitives, Semantic, Space, Typography, Shape, Motion, and Material
files. Presets compose those values; they do not duplicate component code.
Swift folders organize source within one package namespace. Kotlin packages
mirror folders, for example `dev.mobilefoundry.ui.components.feedback.query`.

The [component map](docs/COMPONENTS.md) lists implemented leaf folders across
Forms, Display, Layout, Feedback, Navigation, Overlays, Shells, Patterns and
Charts, using lowercase on Kotlin. All original UI reservations are populated.
Each component gets a directory for its implementation, configuration, variants, and
component-specific documentation. A family is a source folder, not a new build
module. Keep native tests in their package/module test tree and catalog examples
in the app. Avoid a growing flat collection of unrelated UI files.

Component APIs and filenames have no Foundry prefix. Use ActionButton,
LabeledTextField, SubmitButton, Surface, Card and other descriptive names;
component helper types follow the same rule. Package, token/theme and app names
retain their identities. The [component batches](contracts/behavior/ui-components.md)
add native slots, semantic variants, reduced-motion loading and caller-owned
selection/feedback examples. Contextual help, native navigation affordances and
small adaptive layouts reuse the same tokens and native content slots. Choice,
disclosure, passive details and bounded detail screens retain the same naming
and caller-owned state. Password/multiline input, passive requirement/progress
rows and account/onboarding layouts reuse these roles without a new palette. See
[usage examples](docs/blueprints/ui-components.md#using-the-apis).

## Design direction: Foundry Studio

V1 is a quiet workspace for native tools and interactive graphics: porcelain in
light mode, blue graphite in dark mode, and cobalt for action and selection.
Desaturated surfaces give text, media, and future rendered scenes the emphasis.
Bento supplies the organizational reference; Mobile Foundry owns the visual identity.

| Role | Light | Dark |
| --- | --- | --- |
| Ground | `#F3F4F6` | `#10151F` |
| Sunk surface | `#E8EBF0` | `#0B0F17` |
| Panel | `#FAFBFD` | `#181F2C` |
| Raised surface | `#FFFFFF` | `#222C3C` |
| Main ink | `#182132` | `#EEF2F8` |
| Accent / primary fill | `#3155C6` | `#A1B5FF` |
| Primary fill ink | `#FFFFFF` | `#10182C` |

Use cobalt for links, selection, and primary actions; the translucent accent tint
supports selected containers. Dark-mode primary actions use pale cobalt with
dark ink, giving them readable contrast without a neon glow. Sky blue conveys
information, ochre warns, and vermilion marks critical failures. Status also
needs deliberate copy or an accessible label. Keep large backgrounds neutral
and distinguish depth with the four surface roles before adding effects.

Spacing uses a four-point rhythm with a two-point optical step: inline 8,
stack 12, section 24, page 20 points/dp. Radii are 4, 8, 12, and 16; panels use
16. Native system type uses semibold title/heading roles, regular body text,
and monospace for code or technical values. Minimum interactive sizes remain
44 pt on iOS and 48 dp on Android; text and controls can grow with font settings.

Motion uses 120/200/280 ms with a decelerating `(0.2, 0, 0, 1)` curve. Transitions
settle promptly; reduced motion preserves the endpoint with zero duration.

Light and dark keep the same sixteen semantic names. Components ask for
`surfacePanel`, `inkSecondary`, `accent`, or `crit`, rather than palette steps or
RGB values. The [shared fixture](contracts/fixtures/ui/tokens.json) checks parity;
runtime code never loads that checkout file. Add chart, elevation, layering, or
graphics-specific roles when a real consumer can establish their meaning.

## Theme and component use

Install `FoundryTheme` at app composition. Swift views read
`@Environment(\.foundry)`; Compose components read `FoundryTheme.tokens`.
Android also maps the preset into MaterialTheme colors, typography, and shapes.
Swift supplies native font, foreground, tint, and background defaults while
leaving platform chrome to SwiftUI.

Appearance follows the system unless overridden in a subtree. A nested preview
inherits the enclosing theme by default; overrides stay local. Reduced motion
combines the system request, ancestor reduction, and an optional preview flag.
Token-based custom transitions use zero durations when any requests reduction.
Native widgets still own their internal animation behavior.

## Swappable surface themes

Foundry Studio now has **Solid** and **Glass** material styles, independent of
light/dark appearance. Both retain the same color, spacing, type, and motion
tokens. FoundryTheme defaults to Solid; the placeholder app shell starts in
Glass for visualization. Select `style` on FoundryTheme at composition;
nested themes inherit it unless overridden. The catalog home has a Glass
surfaces switch for the app session, also available in the Account tab.
Tokens offers App theme/Solid/Glass preview
selection plus Reduce transparency preview; these overrides stay local.

`Surface` has content and floating roles. Content panels remain opaque
for dense reading content. Floating controls and the form gallery's outer panels
use the selected material. The form gallery supplies a subtle cobalt backdrop
so Glass has content to sample; its native fields/text render above the effect.
Swift uses native regular Liquid Glass on iOS/macOS 26+, with regular Material
on older supported systems. Android uses a recorded Compose backdrop, 16 dp
blur, a neutral tint (88% light / 86% dark), and a highlighted edge on API 31+.
This is a native Android frosted treatment, not Apple's optical rendering.

Android hosts provide a bounded `Backdrop` with background and foreground
slots. Only the background is recorded; controls never sample themselves.
Missing backdrop or API below 31 produces an opaque raised surface. External
SurfaceView/GL output needs a future renderer bridge. Do not assume this first
implementation captures arbitrary GPU scenes. SwiftUI supplies its native
backdrop sampling. OS navigation chrome continues to follow platform styling.

Reduce transparency resolves floating surfaces to solid without changing the
selected style. Swift combines its accessibility environment, ancestor, and
preview flag. Android combines ancestor and caller/preview flags; this slice
does not infer an Android-wide transparency preference. Neither platform lets
a child undo an ancestor reduction. Padding, state, actions, and text sizing
belong to the caller; changing material only replaces the background rendering.

Components receive state and callbacks. They do not select service adapters,
create feature stores, or perform requests. The existing Query component now
uses theme feedback colors and native spacing while retaining its public behavior.
Operation-specific copy stays beside that component as QueryCopy.

Form controls wrap native TextField/OutlinedTextField and Button behavior.
The feature supplies text, help/error, focus/keyboard policy, busy state and
callbacks. Errors use explicit copy and native semantics rather than color alone.
MutationFeedback projects public failures and accepts caller success content;
it never decides whether retrying a write is safe. Form outer panels use the
floating role and follow the app material selection, with opaque fallback for
transparency reduction or unavailable blur. See [forms behavior](contracts/behavior/forms-mutations.md).

## Catalog and checks

Open **Studio → Open catalog → Tokens** in either native app.
The app shell orders Home, Library, Camera, Studio and Account, with native
SwiftUI tab chrome and a floating Compose bottom bar. Camera occupies the middle
position and opens a native viewfinder, then the shared photo editor. The other
destinations retain their placeholders and entry actions. iOS system tab chrome
retains its platform material when Foundry surface style changes. See
[shell behavior](contracts/behavior/app-shell.md).
Tab symbols stay outlined when selected. Swift sets symbolVariants to none
inside each tab label to override the system's automatic fill; Kotlin uses the
existing stroked vectors. Native selection highlights and labels remain visible.
Choose photo opens the system library picker and sends the selected image to
the same editor as camera capture.
The live viewfinder is a native camera surface, independent of the decorative
backdrop. Photo filters set the existing GPU image-adjustment values after capture;
they do not tint the live viewfinder. See [camera behavior](contracts/behavior/camera-photo.md).
System/Light/Dark and Reduce motion
preview affect only the examples below the controls. Inspect color roles,
typography, spacing, radii, action counts, and the bounded position transition.
Changing appearance preserves the sample's action and position state.
At the top of the examples, compare the floating scene controls across material
styles, move the backdrop, select an object, and toggle transparency reduction.
Selection count and scene position survive material and appearance changes.

```sh
make ui-test
make ios-build
make android-build
make android-ui-test
make notes-check
```

`ui-test` runs both native token fixture, contrast, and reduced-motion suites.
Android device tests also cover nested scope, font scaling, minimum touch bounds,
and preview controls. The [learning handoff](notes/patterns/semantic-tokens-and-native-themes.md)
explains the decisions and verification limits. See [Setup](docs/SETUP.md) for
toolchain and emulator prerequisites.

Insights resolves the same roles for native chart strokes/tracks and legends.
Labels, formatted values and trend copy remain native scalable text; ring values
move outside the circle at larger sizes. Native color overrides customize series,
while mark shapes and readable copy keep color from being the only signal.
ChartPanel follows content/floating Solid/Glass roles. Charts add no timer,
interpolated counts or new palette.

Activity compositions resolve existing text, accent, line and spacing roles.
Timeline markers/connectors are decorative; avatar groups expose a supplied
summary. Native refresh/progress controls follow the scoped theme. Expansion
uses immediate native layout rather than adding animation to long text. Screen
scrolling and refresh/page work remain caller-owned.

Editing uses existing label, accent, line and native target roles. WrapLayout
keeps variable token/action widths and grows rows as native copy requires; stable
children retain identity during reflow. Selected row markers supplement explicit
state semantics. Native swipe/menu surfaces indicate action tone, while the
screen's existing ActionBar/ToastBanner follow scoped Solid/Glass presentation.

Communication uses supplied native text and existing theme roles: incoming
messages use the content surface, outgoing messages use accentTint, transfer
failure copy uses crit, and the composer follows the floating Solid/Glass surface.
Logical placement follows native layout direction. Typing dots are decorative and
their opacity pulse disappears under scoped reduced motion. Attachment actions
stay independent of passive thumbnails and selectable message text.

Media browsing uses the same native button, text, space and accent roles.
IconAction supplies semantic meaning for passive icons; PageIndicator is a
passive summary. Rating choices retain touch bounds when wrapping. MediaOverlay
adds a configurable bottom scrim for white copy, while supplied controls keep
native theme surfaces. Native modifiers own media clipping and crop.

Scheduling uses the existing accent/tint/line roles for selected days, caption
and label styles for date/time copy, and native minimum action bounds. Day choices
wrap at narrow widths; agenda copy and actions stack. Date/time modal chrome
inherits the active theme, while native picker presentation remains platform
specific. Caller-formatted copy carries time-zone and availability meaning.

Workspace navigation resolves the existing accent/tint, ink, line and native
target roles. DestinationRail follows the floating Solid/Glass surface role;
breadcrumbs use wrapping native action bounds and passive current copy.
SplitPane receives local bounds, adds token spacing and places primary content
at the logical leading edge. Larger text chooses one pane rather than compressing
both columns. Icons are passive outline artwork supplied by the app; these
components introduce no palette or implicit transition animation.

Tables use opaque content surfaces with sunk header backgrounds, line separators,
native text and independent controls. Fixed column widths include cell padding;
long text grows vertically and the horizontal viewport preserves those widths.
Pagination wraps native minimum-sized actions around supplied page copy. Floating
ledger controls follow Solid/Glass. Sort direction includes explicit accessible
copy in addition to a decorative arrow; the batch adds no color or animation token.

Account compositions use native semantic heading/body/caption styles and passive
outline artwork. Larger text stacks profile artwork above identity copy; session
and permission actions stay below their copy instead of compressing a trailing
column. Profile/device content and permission cards are opaque. The floating
account controls follow Solid/Glass; the account menu retains native presentation
and selected/disabled semantics. No new palette or custom transition is added.

Discovery uses existing accent and semibold text for supplied emphasis runs.
HighlightedText retains one native wrapping text value and accepts native
font/style and emphasis-color overrides. Suggestions use logical directional
affordances; open and bookmark actions retain separate native targets. Result
cards stay opaque, floating search controls follow Solid/Glass, and filter sheets
retain native presentation. No new palette, blur or animation token is added.

Commerce uses native heading/body/caption styles for current/comparison/unit
price copy. Comparison strike-through is supplemented with full supplied
narration. Product rows and order summaries stay opaque; cart controls follow
Solid/Glass. Prices and code/action layouts grow vertically at narrow bounds and
larger text. Native dividers, minimum action bounds and existing theme roles
apply; this batch adds no color, animation or currency-formatting token.

Notifications use the existing accent/tint for count capsules and unread emphasis.
Unread status also has supplied visible copy and complete accessible narration.
Update copy grows vertically beside passive artwork; sibling actions retain
native targets below it. Content cards stay opaque and inbox controls follow
Solid/Glass. Relative times, count caps and plural forms are caller copy, not tokens.

Plans use existing opaque content cards, accent selected outlines and native
choice targets. Feature marks are decorative and availability also has readable
copy. Price, feature and usage text grow vertically; floating preview controls
follow Solid/Glass. UsageMeter uses a determinate native bar only for a supplied
finite fraction; missing/nonfinite fractions hide it. Complete caller narration
retains quota/overflow meaning even when the visual fraction is clamped.

Files use existing accent ink/tint and semantic monospaced caption for passive
FileTypeMark labels. TreeRow selected tint, native chevrons and outlined favorite
actions reuse existing tokens. Logical indentation defaults to 16 points/dp per
level with a 48 cap; large text stacks passive artwork above copy and grows
vertically. Floating controls sample the scoped Glass theme, while file content
cards remain opaque. No new palette, file artwork loader or implicit transition
is introduced.

Sharing uses existing avatar, semantic identity/heading/body/code styles and
accent/status roles. MemberRow leaves access/actions outside the passive identity
group, stacks avatar above growing copy for larger text and follows logical
alignment. ShareLinkCard keeps opaque content and limits native text selection
to the monospaced link. Floating preview controls use scoped Glass surfaces;
role menus, confirmations and clipboard presentation use native mechanisms.
No new palette or implicit membership animation is introduced.

Playback uses the existing opaque Card, semantic heading/caption text and native
IconAction/ValueSlider controls. Passive artwork defaults to an 80-point/dp square;
larger text stacks it above growing identity. Transport glyphs occupy 24 points/dp
inside native action targets, with reflow at narrow widths. Labels and state copy
carry meaning beyond glyphs. Floating preview controls use scoped Glass surfaces.
No palette, playback animation or timeline clock token is introduced.

Verification uses an opaque content card, existing semantic heading/body/code
styles and native text input/action targets. Larger text stacks passive delivery
artwork above copy and grows help/error/status vertically. Code digits retain
left-to-right order inside logical RTL layouts; surrounding labels follow the
host. Floating preview controls follow Glass. Input remains one native field,
with no per-digit focus boxes, new palette or implicit countdown animation.

## Stack and shell styling

VerticalStack defaults to `space.stack`; HorizontalStack defaults to
`space.inline`. Explicit spacing and native alignment remain caller choices.
SectionDivider defaults to the theme line color and one point/dp thickness;
it is decorative and has no action or accessible meaning. Bound vertical
dividers to an explicit row height.

AppShell is a layout composition, with a passive background and independent
content/navigation slots. A scene behind floating surfaces makes Glass visible
on Kotlin through the existing Backdrop provider. Swift TabBar retains system
TabView chrome and its native material; the Solid/Glass switch governs shared
surfaces. Hosts own safe areas and avoid applying the same system inset twice.

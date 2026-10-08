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

Add component families as their first implementation arrives: Forms, Display,
Layout, Navigation, Overlays, and Shells, using lowercase on Kotlin. Each
component gets a directory for its implementation, configuration, variants, and
component-specific documentation. A family is a source folder, not a new build
module. Keep native tests in their package/module test tree and catalog examples
in the app. Avoid a growing flat collection of unrelated UI files.

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
tokens. The default is Solid. Select `style` on FoundryTheme at composition;
nested themes inherit it unless overridden. The catalog home has a Glass
surfaces switch for the app session. Tokens offers App theme/Solid/Glass preview
selection plus Reduce transparency preview; these overrides stay local.

`FoundrySurface` has content and floating roles. Content panels remain opaque
for dense reading content. Floating controls and the form gallery's outer panels
use the selected material. The form gallery supplies a subtle cobalt backdrop
so Glass has content to sample; its native fields/text render above the effect.
Swift uses native regular Liquid Glass on iOS/macOS 26+, with regular Material
on older supported systems. Android uses a recorded Compose backdrop, 16 dp
blur, a neutral tint (88% light / 86% dark), and a highlighted edge on API 31+.
This is a native Android frosted treatment, not Apple's optical rendering.

Android hosts provide a bounded `FoundryBackdrop` with background and foreground
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

Open **Tokens** in either native catalog. System/Light/Dark and Reduce motion
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

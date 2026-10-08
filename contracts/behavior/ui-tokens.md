# Native UI tokens and theme

Status: Established for the first token preset, 2026-10-08.

## Ownership and organization

Follow Bento's distinction between palette primitives, semantic tokens, theme
selection, and components grouped by responsibility. Native UI implementations
use Styles/Tokens (styles/tokens), Styles/Presets (styles/presets), Theme (theme),
and Components/Feedback (components/feedback). Future forms, display, layout,
and other component families are added when implemented. Catalog examples stay
in their application. Swift directory names do not create language namespaces;
Kotlin packages follow the corresponding folder split.

V1 is Foundry Studio, revised 2026-10-08: porcelain light surfaces, blue graphite
dark surfaces, cobalt actions, and restrained sky/ochre/vermilion status colors.
Bento informs directory and semantic organization. The native palette, spacing,
shape, and timing values belong to Mobile Foundry. See [Styles](../../STYLES.md)
for the design direction. Chart colors, elevation, layering, and density remain
future capabilities.

## Surface materials

Material definitions live in Styles/Tokens/Material and styles/tokens/Material.
Solid (default) and Glass are selectable styles orthogonal to appearance.
They preserve V1 colors and all other scales. `materials.content` is always
solid; `materials.floating` is glass only when Glass is selected and transparency
is not reduced. Surface renderers live in Components/Layout/Surface and
components/layout/surface. Hosts choose content or floating responsibility.

Swift floating surfaces use regular Liquid Glass on iOS/macOS 26+, regular
Material on earlier supported systems, and an opaque raised surface on reduction.
Android uses a blurred, tinted Compose backdrop on API 31+ when the host provides
Backdrop; unsupported APIs and missing sources fall back to opaque raised
surfaces. The source contains the background slot only, never the foreground
controls. Source changes update the sampled backdrop. Compose GraphicsLayers
are scoped to the host composition. External renderer surfaces are outside this
contract. Text is drawn after the backdrop, without applying blur to controls.

Nested themes inherit material style unless explicitly overridden. Transparency
reduction ORs ancestor and explicit flags; Swift additionally reads system Reduce
Transparency. Android exposes a caller flag and catalog preview, with no claimed
system preference integration. A child cannot undo reduction. Switching materials
preserves example state and callbacks. Solid-palette contrast checks do not prove
contrast over every native glass backdrop. Platform navigation chrome remains
owned by the OS.

## Semantic colors

Both platforms expose surfaceGround, surfaceSunk, surfacePanel, surfaceRaised,
ink, inkSecondary, inkMuted, line, lineStrong, accent, accentTint, fill, fillInk,
info, warn, and crit. Components consume roles; primitive RGB/alpha values live
in palette/preset code. Light and dark mappings are canonical shared fixtures.
Separators are decorative, not a sufficient boundary for an interactive field.
Main, secondary, and muted ink on the ground/panel, accent on the panel, fillInk on
fill, and status ink on the panel meet 4.5:1 in the first preset's sRGB contrast
checks. Material inverse surfaces use the opposite appearance's accent over
the current ink color; that pair also meets 4.5:1. Status meaning requires text
or an accessible label.

## Native scales

Spacing uses twelve steps: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 96
points/dp. Semantic aliases are inline=8, stack=12, section=24, page=20.
Radius steps are 4, 8, 12, 16, with a pill role; panels use 16. Interactive
minimums are 44 points on iOS and 48 dp on Android. These are minimums, not fixed
control/text heights.

Typography exposes title, heading, body, label, caption, and code roles mapped
to native system styles, with semibold titles and headings. SwiftUI Dynamic Type
and Compose font scaling remain enabled; no bundled fonts or fixed web text sizes
are introduced.

Motion durations are 120, 200, and 280 milliseconds with the standard curve
(0.2, 0, 0, 1). Reduced motion resolves all three to zero. iOS reads its
accessibility environment; Android observes animator duration scale and treats
zero as reduced motion. An explicit preview flag can additionally reduce motion,
never override a system request to reduce it. This governs token-based custom
transitions, not every native widget's internal animation.

## Theme scope and catalog

Applications install the reusable FoundryTheme at composition. It follows the
system light/dark preference unless a subtree explicitly selects an appearance.
Nested preview themes do not mutate the app's global selection or preferences.
Android maps the semantic colors/type/shapes to MaterialTheme and supplies
extra tokens through a composition local. Wallpaper-derived dynamic colors are
not selected by this deterministic preset. Swift supplies tokens through the
environment, native text defaults and tint; platform chrome remains native.

The Tokens destination offers System/Light/Dark, a reduced-motion preview,
color swatches, typography, spacing, shape, native action samples and a bounded
motion example. The existing async component consumes theme spacing and color
roles. Theme changes must preserve its data and action behavior.
The home switch selects Glass for the app session. Tokens additionally offers
App theme/Solid/Glass and Reduce transparency preview, with a movable scene and
floating control sample. Reading panels stay solid for either style.
The Forms and mutations gallery selects floating outer panels and supplies its
own decorative backdrop, so the app selection is also visible in that workflow.

## Verification

Shared fixtures cover both palettes, scales and platform minimums. Native tests
check mappings, contrast and reduction. Compose device tests check theme
propagation, nested override isolation, font-scale behavior and catalog controls.
iOS simulator checks cover appearances and interactions. These checks do not
constitute a full accessibility or physical-device audit.
Shared material cases also cover both styles and transparency reduction. Android
device checks exercise style inheritance, local overrides, retained scene state,
translated backdrop sampling, scene updates, and interactive opaque fallback.

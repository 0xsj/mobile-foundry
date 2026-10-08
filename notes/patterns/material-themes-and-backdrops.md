# Material themes and backdrop ownership

A material theme changes how a surface renders while its content, state, and
interaction contract remain stable.

## Origin and evidence

Added 2026-10-08 after the user requested a toggleable glass treatment alongside
Foundry Studio. The shared material fixture establishes role selection, not
pixel parity. Native test/build and runtime outcomes are recorded in the UI
walkthroughs. Framework behavior comes from [Apple's glass modifier](https://developer.apple.com/documentation/swiftui/view/glasseffect(_:in:))
and [Compose drawing documentation](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers).

## What and why

Color and material are separate axes. Light/dark maps readable semantic colors;
Solid/Glass chooses the treatment of floating controls and form outer panels.
Explicit content-role reading panels remain opaque in both styles. Validation
and submission ownership remain independent of decorative choices.

The host knows what is behind an overlay. SwiftUI supplies native sampling;
Compose's blur operates on drawn content, so the Android host explicitly exposes
a background source. Record that source separately from foreground controls.
Blurring the complete control tree would soften text and risk feedback when an
overlay captures itself. A renderer outside that tree needs a separate bridge.

## Example

A scene has one geometric background and a floating control panel. Moving the
scene changes the sampled backdrop. Selecting an object increments a count.
Switching to Solid or reducing transparency replaces the panel's material,
while the same control content and count remain alive. Keep the content outside
conditional material branches so changing the background does not reconstruct
its state owner.

## Gotchas

- Propagating a theme value does not ensure a screen uses it. The initial form
  gallery selected always-opaque content surfaces, so the user saw no change
  after choosing Glass. The 2026-10-08 correction selects floating outer panels
  and supplies a catalog backdrop. Android needs that source as well as a glass
  role; otherwise it correctly falls back to solid. Check actual screen pixels
  alongside theme scope and preserve the feature owner across recomposition.
- Glass over a flat neutral background can look almost solid. Inspect it over a
  backdrop with geometry and move that backdrop to check live sampling.
- A lower alpha alone does not supply blur or legibility. Neutral tint and an
  opaque fallback are explicit parts of the Android implementation.
- Keep transparency reduction separate from motion reduction. A user can
  request either; a child cannot undo an inherited request.
- Local coordinates differ between source and overlay. Sample the translated
  source, and verify an overlay placed away from the source origin.
- A semantic assertion cannot establish a blur effect. Pixel sampling can
  establish source placement and updates, but still does not measure frame cost
  or replace a human visual/accessibility review.

## Used in and related

Follow the [Swift UI walkthrough](../../frontend/swift/notes/modules/packages/FoundryUI/README.md)
and [Kotlin UI walkthrough](../../frontend/kotlin/notes/modules/project/core/ui/README.md)
for source, checks, and native limits. Compare [token ownership](semantic-tokens-and-native-themes.md).
The [token contract](../../contracts/behavior/ui-tokens.md) owns the supported
behaviors; [Styles](../../STYLES.md#swappable-surface-themes) owns organization
and design usage.

# Semantic tokens and native themes

A reusable UI system separates what a visual value means from its palette value
and from the native mechanism that supplies it to a component.

## Origin and evidence

Established during the 2026-10-08 token slice after inspecting Bento's web v1
tokens, theme selection, component families, and kitchen-sink organization.
The native implementations share fixtures and pass palette, contrast, and motion
checks. Android device tests and manual iOS interactions additionally exercise
scope and sample-state preservation. Those checks support this implementation;
they do not establish every accessibility or device behavior.

The same day's Foundry Studio revision separates that organizational reference
from visual identity. The initial neutral/green values resembled Bento's Supabase
palette; the user requested an independent direction for native and graphics work.

## What and why

The flow is palette primitive → semantic role → preset → scoped theme → component.
A primitive is an RGB/alpha value. A role such as `inkSecondary` expresses purpose.
A preset maps each role for light and dark. A theme selects that mapping and
adapts it to SwiftUI or Material. Components ask for roles, so appearance changes
do not require a second component implementation.

Keep token-family definitions separate from complete preset mappings. Keep theme
scope separate from feature state. Catalog controls can wrap an example in a
different appearance while the surrounding app retains its own theme, and the
example's action count stays alive. Provider choice and request lifetime have
no role in this resolution.

## Example

A failed read uses critical ink for its deliberate public message. The component
does not select a red palette step or diagnose the underlying dependency. A
light or dark preset supplies the color; the feature supplies recovery callbacks.
This preserves the same seam used by memory and HTTP services.

Space, type, shape, and motion need native adaptation too. Reuse a spacing scale
in points/dp, map typography roles to scalable system text, and treat control
size as a minimum. A dense web control height is unsuitable as a fixed native
text height. Reduced motion changes transition duration while retaining the
endpoint and user action.

## Visual identity and semantic stability

Foundry Studio uses porcelain/blue-graphite surfaces and cobalt actions. Neutral
canvases keep attention on tools and rendered content. Sky, ochre, and vermilion
remain status signals rather than competing brand accents. A four-point spacing
rhythm, 16-point/dp panels, semibold native headings, and decelerating motion give
the palette a consistent layout and interaction context.

This changes the existing V1 preset in place while its first consumers are still
catalogs. Public semantic roles and the directory split continue to serve the
components. The important adaptation is foreground/background pairing: dark
mode uses pale cobalt with dark action ink. A light-mode accent cannot simply be
reused over an inverse dark surface; the opposite appearance's accent supplies
readable inverse action text. Both native contrast suites check that pair and
muted text on ground/panel surfaces alongside the existing promises.

The catalog derives spacing captions from resolved tokens so explanatory text
does not retain an old scale after a preset revision. The [style direction](../../STYLES.md#design-direction-foundry-studio)
records the choices; the canonical fixture and native checks supply value evidence.

## Gotchas

- A token name does not prove contrast. Check the actual foreground/background
  pairs, including alpha composition. Decorative separators are not suitable
  field-boundary guarantees.
- A visible button rectangle and its touch bounds can differ. Test the property
  being promised, and still inspect crowded layouts for overlapping hit areas.
- A nested theme must inherit surrounding appearance and reduction unless it
  explicitly overrides appearance. A preview cannot force-enable motion against
  an ancestor or system request.
- Shared semantics do not imply identical pixels. Native typography, controls,
  navigation chrome, and accessibility settings remain platform concerns.
- One demonstrated preset is enough to establish the split. More presets,
  chart colors, elevation, and GPU roles need their own consumers and checks.

## Used in and related

Read the [Swift UI walkthrough](../../frontend/swift/notes/modules/packages/FoundryUI/README.md)
and [Kotlin UI walkthrough](../../frontend/kotlin/notes/modules/project/core/ui/README.md)
for actual source and test links. Compare [query/rendering ownership](query-state-and-rendering.md)
and [fixture versus native evidence](../techniques/shared-fixtures-and-native-adapters.md).
The [token contract](../../contracts/behavior/ui-tokens.md) owns the exact roles
and guarantees; [Styles](../../STYLES.md) owns directory placement.

# Native UI package walkthrough

FoundryUI renders query state through native SwiftUI rows, caller copy and a
domain content builder, without owning requests or services. It also owns
semantic tokens, one preset, and a scoped native theme.

## Origin and reading order

Extracted 2026-10-08 using Xcode 26.2 and Swift 6.2.3. Read
[the contract](../../../../../../contracts/behavior/query-ui.md),
[manifest](../../../../packages/FoundryUI/Package.swift), and
[QueryContent](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Query/QueryContent.swift).
Then compare [NotesScreen](../../../../apps/FoundryCatalog/Sources/Notes/NotesScreen.swift)
with [QueryCatalogView](../../../../apps/FoundryCatalog/Sources/Query/QueryCatalogView.swift).

## Walkthrough and gotchas

QueryCopy carries operation-specific strings and action labels. QueryContent
switches on state, labels progress as loading or refreshing, and renders a
retained snapshot through either empty copy or the content builder. Failed
state projects publicInfo before presenting its message. Buttons forward
callbacks; construction and state changes invoke no action.

The notes feature supplies note rows and list emptiness. The gallery supplies
one string and string emptiness. Group emits rows inside the host's Section;
the component introduces no nested List or task. The host still owns scrolling,
theme, observation, and lifecycle. Copy can be localized before injection.
See [content builder mechanics](../../../language/swift-generic-query-state-and-content-builders.md).

## Verification and limits

`make ios-test` compiled the package and catalog and passed seven store tests.
Simulator interactions on iPhone 17 Pro/iOS 26.2 confirmed idle/initial loading,
content/empty, refreshing both snapshots, failure retaining both snapshots,
generic internal failure copy, Retry, and Cancel restoration. Notes still showed
both content rows through the extracted component. A current screenshot showed
the populated gallery's native List layout; screenshot output was usable in
this slice, unlike the earlier notes session.

These are semantic/manual presentation checks, not automated SwiftUI rendering
tests or a VoiceOver, large-text, dark-mode, and physical-device audit. Android
instrumentation independently covers the shared presentation matrix. The token
slice below extends appearance checks; forms and further controls remain future work.

## Token families and native theme

The 2026-10-08 token slice follows [Styles](../../../../../../STYLES.md) and
[the token contract](../../../../../../contracts/behavior/ui-tokens.md).
Read the actual source in this order:

1. [Primitives](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Primitives.swift)
   stores owned sRGB/alpha values and internal palette steps.
2. [Semantic](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Semantic.swift)
   defines the role set and resolved token bundle.
3. [Space](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Space.swift),
   [Typography](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Typography.swift),
   [Shape](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Shape.swift), and
   [Motion](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Motion.swift)
   hold native scales and reduction behavior.
4. [V1](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Presets/V1.swift)
   maps both appearances without duplicating components.
5. [FoundryTheme](../../../../packages/FoundryUI/Sources/FoundryUI/Theme/FoundryTheme.swift)
   resolves environment inputs and supplies descendant defaults.
6. [Token tests](../../../../packages/FoundryUI/Tests/FoundryUITests/TokensTests.swift)
   compare the portable fixtures, contrast pairs, and reduced-motion values.
7. [TokenCatalogView](../../../../apps/FoundryCatalog/Sources/Tokens/TokenCatalogView.swift)
   owns preview controls and native samples, outside the reusable package.

The app entry installs the provider. A second provider wraps only the catalog
examples, keeping controls outside the appearance override. System Font styles
retain text scaling; point-based minimums allow content growth. Read
[SwiftUI environment mechanics](../../../substrate/swiftui-token-environment.md)
for property wrappers, key paths, inheritance, and reduction.
The catalog's action-label minimum is inside Button's label, with a rectangular
content shape; an outer layout frame alone is not the sizing example being used.

QueryContent and [QueryCopy](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Query/QueryCopy.swift)
now sit together under Components/Feedback/Query; their public names are unchanged.
QueryContent reads semantic feedback colors from the environment, while the
host retains native List row layout. Feature observation and request lifetime
are unaffected by the directory/theme changes.

Three package tests pass. Both the final simulator app build and seven store
regressions pass. Manual iOS 26.2 checks showed light/dark swatches, zero durations
with preview reduction, action/position changes, and those values surviving
Dark → Light. A screenshot showed the dark preview beneath light controls.
There is no automated SwiftUI scope/rendering test or measured iOS touch-bounds
check in this slice; exhaustive Dynamic Type/VoiceOver/device checks remain open.

## Foundry Studio revision

Revised 2026-10-08 after the initial palette was judged too close to Bento's
Supabase styling. V1 now maps porcelain and blue-graphite surfaces, cobalt actions,
and sky/ochre/vermilion statuses. Space uses a four-point rhythm with page=20,
section=24, stack=12; panels use radius 16. Titles/headings are semibold native
styles, and standard custom motion uses 200 ms deceleration. The iOS AccentColor
asset mirrors the preset's light/dark accent as the app fallback.

The existing three package tests pass with the revised fixtures, including added
muted-text and inverse-accent contrast pairs. `make ios-build` compiles the updated
consumer and asset catalog. See [the shared pattern](../../../../../../notes/patterns/semantic-tokens-and-native-themes.md#visual-identity-and-semantic-stability)
for why directory parity does not require inheriting another product's palette.
Simulator accessibility state confirmed both revised palettes, the new scale
captions and timings, action/position changes, and zero durations after reduction.
Screenshot capture returned blank images during this revision, so it supplies
no new visual-layout evidence beyond the earlier preset's inspection.

## Swappable material slice

Added 2026-10-08. Read [Material](../../../../packages/FoundryUI/Sources/FoundryUI/Styles/Tokens/Material.swift),
the updated provider and V1, then [FoundrySurface](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Layout/Surface/FoundrySurface.swift).
The surface's content/floating role determines whether it can use the selected
glass treatment. Caller content stays in one stable view structure while its
background switches. Padding and callbacks remain caller-owned.

The app entry owns a session-scoped Glass surfaces switch; CatalogView exposes
its binding. TokenCatalogView has an optional local style (nil means inherit),
explicit transparency preview, and a geometric scene with independent selection
and position state. ViewThatFits can arrange scene actions vertically if their
horizontal form does not fit. Light/dark, material, and motion are separate axes.
See [framework mechanics](../../../substrate/swiftui-token-environment.md#glass-material-extension)
and [shared material ownership](../../../../../../notes/patterns/material-themes-and-backdrops.md).

Four package tests pass, including shared material role/reduction cases and
palette/motion stability. Both native apps build. iOS 26.2 accessibility checks
observed app-theme inheritance, local Solid selection, reduced transparency
resolving Glass to Solid, and scene state surviving those changes and dark
appearance. Screenshot capture still returned blank white output, so no new
visual inspection of glass is claimed. Older-system Material, live accessibility
setting changes, large-text layout, VoiceOver, and physical GPU cost remain
unverified. The scene is a native 2D material demonstration, not a 3D renderer.

## Native forms and write feedback

The 2026-10-08 forms slice adds
[FoundryTextField](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/TextField/FoundryTextField.swift),
[FoundrySubmitButton](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Forms/SubmitButton/FoundrySubmitButton.swift), and
[MutationFeedback](../../../../packages/FoundryUI/Sources/FoundryUI/Components/Feedback/Mutation/MutationFeedback.swift).
The first wraps native editing with persistent labels, help/error and caller
focus/submit callbacks. The second renders busy copy/spinner and a disabled
native action. Feedback projects public failure copy or caller success content.
None imports services, performs validation or launches requests.

These live in separate Forms and Feedback families following Styles. Native
text and minimum dimensions can grow. The form gallery's corrected outer panels
select floating FoundrySurface so Solid/Glass affects their material; explicit
content-role panels still stay opaque. Read [FocusState and native submission](../../../substrate/swiftui-form-focus-and-submit.md)
for why text/focus bindings differ and why the feature action runs synchronously.
`make ios-test` compiles the real consumer and passes thirteen feature tests;
the UI package's four token/material tests remain separate from form behavior.
Actual keyboard and error presentation require simulator/device checks; compile
success alone is not layout or VoiceOver evidence.

## Next questions

Which failure needs a feature-specific recovery action beyond Retry? Which
layout choices belong to the host rather than QueryContent? Read
[the shared pattern](../../../../../../notes/patterns/query-state-and-rendering.md).
How should a rendered scene provide its backdrop without moving frame ownership
into the UI theme? Which floating controls need material interaction or grouping?

## Reserved component catalog

The 2026-10-08 [component map](../../../../../../docs/COMPONENTS.md) adds
.gitkeep-only planned leaf directories grouped like Bento. These do not add
public APIs, variants, tests or module dependencies. The native TabView shell
belongs to app composition; Swift's Navigation/TabBar and Shells/AppShell are
reserved for a later justified reusable consumer. See
[tabs and feature lifetime](../../../../../../notes/patterns/tabs-and-feature-lifetime.md).

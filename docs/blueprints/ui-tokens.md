# Native token construction specification

## Authority and scope

The [token contract](../../contracts/behavior/ui-tokens.md) owns this slice.
Use [Bento styles](../../../bento/frontend/STYLES.md) as the organization reference.
[Foundry Studio](../../STYLES.md#design-direction-foundry-studio) owns the native
visual direction and token values. One native preset and its real consumers are
required before forms and mutations.

## File and dependency map

Within FoundryUI and core/ui, add Styles/Tokens (styles/tokens) files for
Primitives, Semantic, Space, Typography, Shape and Motion. Presets/V1 composes
the initial mappings. Theme/FoundryTheme owns native scope and platform theme
adaptation. Move QueryContent and QueryCopy into Components/Feedback/Query on Swift
and components/feedback/query on Kotlin. Keep Swift's public symbols; update
Kotlin imports to its new role package. Query/state packages remain UI-independent.

Catalog Tokens files own controls and examples, not runtime tokens. The Swift
application installs FoundryTheme at its root. The Android catalog's theme
wrapper delegates to the reusable theme; remove generated purple/type defaults.
Update source links, organization, contracts, module walkthroughs, and reading
indexes with the final layout.

## Behavior and order

System appearance is the default; nested preview selection resolves locally.
System reduced motion or explicit reduction yields zero-duration custom motion.
Android settings observation is registered only while composed and removed on
disposal. Theme resolution does not start work or reset feature state.

## Checks and completion

Add canonical shared token fixtures and native tests for palette/scales,
contrast and reduced motion. Build both apps, retain the async UI regressions,
and exercise token catalog appearance/motion controls. Verify native font scaling
and nested theme scope on Android; inspect iOS rendering and scope manually.
Run notes-check and diff checks. Record actual outcomes and limits in notes.

## Completion evidence

Implemented 2026-10-08. Three token tests pass on each platform, covering canonical
palettes/scales, promised contrast pairs, and zero-duration reduction. Both apps
build; seven iOS store and eight Android notes ViewModel regressions pass.
All fourteen Android instrumented tests pass, including four token/theme checks
for nested scope, font scaling, touch bounds, controls, and state preservation.
Manual iOS checks observed scoped light/dark palettes, reduction, action/position
changes and preserved state after an appearance change; a screenshot confirmed
the scoped dark preview below light catalog controls. Comprehensive accessibility,
physical-device behavior, and iOS touch-target measurement remain unverified.

Foundry Studio revision, 2026-10-08: both native builds and the three token tests
per platform pass with the new palette/scales. Contrast checks now include muted
text and inverse accents. The first Android device run passed thirteen checks
and timed out in existing notes navigation; that check passed in isolation and
the final full rerun passed all fourteen. iOS accessibility state confirmed the
revised light/dark values, scale captions, actions, and reduced motion; screenshot
capture was blank during this revision. See the UI walkthroughs for evidence limits.

The same day's material slice adds a Material token family, Solid/Glass theme
selection, content/floating surfaces, a bounded Android backdrop host, app/local
catalog controls, and transparency reduction. Four UI unit tests per platform
pass. Both apps build. New Android device checks and iOS semantic interactions
cover material scope and preserved state; the UI walkthroughs record pixel
coverage, existing HTTP test timing, and blank iOS screenshots. External renderer
integration and device performance remain future work.
The final full Android device run passes all seventeen checks, including blur
edge smoothing, source translation and updates, and opaque fallback. Final
notes-check passes with 89 documents, 543 local links and 16 labeled examples;
diff checks pass. The device run's connection delay and slow query test remain
recorded verification limits.

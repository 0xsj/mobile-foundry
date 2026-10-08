# Compose token scope, motion, and touch bounds

A Compose theme can supply extra semantic tokens while adapting Material's
native components, and its tests must distinguish layout bounds from touch bounds.

## Origin and versions

Inspected and exercised 2026-10-08 with Kotlin 2.3.20, AGP 9.0.1, Compose BOM
2026.03.01 resolving Compose UI 1.10.6, and API36_Test/Android 16. Four token/theme
instrumented tests pass alongside the ten existing catalog checks. Three host
token tests independently cover canonical values, contrast, and reduction.

## Theme mechanism

`staticCompositionLocalOf` creates a scoped value channel. A nullable default
distinguishes an absent provider from an inherited light preset. A theme resolves
explicit appearance, then inherited appearance, then the system preference.
Using the system preference at every nested provider would lose the parent's
preview override.

`CompositionLocalProvider(LocalTokens provides tokens)` supplies the value only
to its content. `provides` is Kotlin infix-call syntax; `.current` reads the
nearest provider during composition. A composable getter on a theme object
offers convenient access without making the selected tokens global state.

```kotlin
// Conceptual: consume semantic values beneath the theme provider.
@Composable
fun SupportingText() {
    val tokens = FoundryTheme.tokens
    Text("Saved on this device", color = tokens.colors.inkSecondary.color,
        style = tokens.typography.caption)
}
```

Map the same preset into MaterialTheme colorScheme, typography, and shapes so
native buttons and labels participate. Extra roles remain available through the
composition local. Deterministic preset mapping avoids wallpaper-derived values
changing a fixture's meaning. It does not guarantee every native widget has
identical styling or that every possible color pair meets contrast targets.
[Android's custom design-system guide](https://developer.android.com/develop/ui/compose/designsystems/custom)
documents the wrapper/provider approach.

## Motion observation and disposal

The provider reads `Settings.Global.ANIMATOR_DURATION_SCALE`; zero requests
zero-duration custom token transitions. A `ContentObserver` updates remembered
state on the main thread. `DisposableEffect(resolver)` registers the observer
for that resolver and `onDispose` unregisters it when the scope leaves composition.
Re-reading after registration closes the initial read/register interval. This
observes an operating-system preference; it never writes the setting.

Explicit preview reduction is ORed with ancestor and system reduction. Tests
request reduction locally, so they work without changing emulator preferences.
Live operating-system setting changes and observer disposal are source-inspected
paths rather than a separately exercised settings-toggle scenario in this slice.

## The failed assertion and correction

The first font/touch test asserted that a Material button's layout height was
at least 48 dp. It observed 40 dp and failed. That was the wrong measurement:
Compose can expand a native control's touch area beyond its visible layout.
[Android's accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
describe the expansion and the need for adequate space between controls.

Inspection of the installed UI 1.10.6 AAR confirmed public
`SemanticsNode.touchBoundsInRoot`. The corrected test checks its pixel height
against 48 dp converted with the actual device density. It changes only fontScale
from 1 to 2, confirms that measured text height grows, and checks touch bounds
at both scales. All fourteen device tests then passed. Online API references
can describe newer alpha test helpers; inspect the pinned artifact before
assuming a convenience assertion is available.

These checks establish the sample's font/touch behavior, nested scope, Material
background mapping, and catalog interaction. They do not audit TalkBack, all
large-font layouts, crowded hit areas, or physical devices.

## Used in and related

The [UI module walkthrough](../modules/project/core/ui/README.md) links the actual
theme and tests. Compare [Compose effect lifetime](okhttp-and-compose-effect-lifetime.md),
[Gradle versioning](gradle-and-android-bootstrap.md), and the
[shared theme pattern](../../../../notes/patterns/semantic-tokens-and-native-themes.md).

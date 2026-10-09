# Compose shell slots and inset ownership

Claim: bounded shell slots can keep navigation outside scrolling content while
the app applies system insets once and owns feature state.

Origin/evidence: 2026-10-09, Kotlin 2.3.20, AGP 9.0.1, Compose BOM 2026.03.01,
Material3 1.4.0 and Android API 36 emulator (min SDK 24).
[Android's Material inset documentation](https://developer.android.com/develop/ui/compose/system/material-insets)
describes NavigationBar's default bottom/horizontal insets and explicit
windowInsets overrides. The repository sets zero internal insets and applies
safe drawing padding in the app shell host.

## What and why

AppShell requires bounded BoxWithConstraints and creates an existing Backdrop
context. A Column gives weight to the content Box while the navigation slot
measures independently. Content and background retain BoxScope; the consumer
chooses scrolling and placement. The passive background clears semantics so it
does not duplicate page content. It must not contain interactive controls.

VerticalStack/HorizontalStack keep ColumnScope/RowScope instead of replacing
native weight and alignment. A nullable spacing override resolves to theme
stack/inline tokens. SectionDivider projects the theme line with explicit
inset/thickness/color; it has no interactive or accessible meaning.

```kotlin
// Excerpt: internal shell layout leaves bottom navigation unweighted.
Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    navigation()
}
```

## Gotchas and actual use

An unbounded vertical scroller is not a valid shell host. Put scrolling inside
the content slot. Apply safe drawing insets in one owner rather than stacking
Material defaults and host padding. Tab IDs/selection must be admitted before
changing item lists; duplicate selection emits no callback. Keep feature values
above route returns and save only intentional fixture values, not native handles.

[Shell source](../../project/core/ui/src/main/kotlin/dev/mobilefoundry/ui/components/shells/appshell/AppShell.kt),
[component tests](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ShellComponentTest.kt)
and [gallery tests](../../project/app/src/androidTest/java/dev/mobilefoundry/catalog/ui/components/ShellCatalogTest.kt)
show the behavior. Five focused shell/gallery cases and eight UI package cases
pass, including both existing app-shell regressions. Observed geometry covers
spacing overrides, RTL and nonoverlapping slots; saved-state checks retain page
markers/selection/visibility/spacing across recreation and routes. Other Android
gallery families, TalkBack traversal, physical devices and older OS execution
were not checked in this batch.

Related: [UI walkthrough](../modules/project/core/ui/README.md#stacks-separators-and-shells),
[app walkthrough](../modules/project/app/README.md#shell-primitives-gallery),
[shared ownership](../../../../notes/patterns/shell-chrome-and-feature-lifetime.md).

Next questions: which inset owner should handle a real editing keyboard, and
when should adaptive navigation introduce a rail rather than another bottom bar?

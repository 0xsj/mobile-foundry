# Compose tabs and presentation owners

Claim: saveable destination identifiers and a presentation-scoped ViewModelStore
let tab selection outlive recomposition while catalog feature owners end when
that presentation is removed.

## Origin and versions

Observed 2026-10-08 with Compose BOM 2026.03.01, Navigation3 1.0.1,
Lifecycle 2.10.0 and Android 16/API36_Test. See the primary
[navigation-bar guide](https://developer.android.com/develop/ui/compose/components/navigation-bar),
[state saving guide](https://developer.android.com/develop/ui/compose/state-saving),
and [Navigation3 state guide](https://developer.android.com/guide/navigation/navigation-3/save-state).

## What and why

NavigationBarItem supplies the selected state, label and click semantics; it
does not own routing. The shell saves a String destination ID and a Boolean
catalog-presentation flag. Only one placeholder is composed at a time.

```kotlin
// Excerpt
var selectedId by rememberSaveable { mutableStateOf(ShellTab.HOME.name) }
var catalogPresented by rememberSaveable { mutableStateOf(false) }
```

See [the app walkthrough](../modules/project/app/README.md#four-tab-placeholder-shell)
for the source. While the catalog is shown, a local ViewModelStoreOwner scopes
Navigation3 entry models to that presentation. DisposableEffect clears its
store on removal, including configuration-driven removal. Its feature models
are therefore recreated after Activity recreation; saved navigation can still
restore. This deliberately does not promise retained feature drafts.

The bottom bar asks the existing floating surface to render its background.
Backdrop captures the static wash only; the navigation items are
foreground content, avoiding self-sampling. The host supplies system insets,
and the internal NavigationBar uses zero additional insets to avoid duplication.

## Gotchas and evidence limits

A root Back callback cannot blindly remove the last navigation entry. Detail
Back pops; root Back closes this presentation. At the shell root, Android
owns system Back. rememberSaveable is saved-instance state, not durable storage.
The shell tests exercise saved-state restoration and both back/close paths;
they do not establish authenticated navigation, deep links or tablet behavior.
Glass still falls back to opaque when blur/backdrop support is absent.

## Used in and related

See [the app walkthrough](../modules/project/app/README.md#four-tab-placeholder-shell),
[UI walkthrough](../modules/project/core/ui/README.md),
[backdrop layers](compose-backdrop-layers.md), and
[shared lifetime pattern](../../../../notes/patterns/tabs-and-feature-lifetime.md).

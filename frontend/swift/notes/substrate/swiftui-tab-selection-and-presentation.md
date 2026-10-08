# SwiftUI tab selection and presentation

Claim: a scene-scoped selected tab and a separately presented navigation stack
give the placeholder shell native navigation semantics without coupling its
destinations to the UI package.

## Origin and versions

Observed 2026-10-08 using the repository's Swift 6 build, iOS 17 deployment
target and iOS 26.2 simulator. Apple's
[TabView documentation](https://developer.apple.com/documentation/swiftui/tabview)
describes selection-driven tabs; [SceneStorage](https://developer.apple.com/documentation/swiftui/scenestorage)
provides scene-scoped restoration. Apple's
[WWDC25 native design session](https://developer.apple.com/videos/play/wwdc2025/323/)
documents floating Liquid Glass tab bars on iPhone with the new SDK.

## What and why

TabView binds selection to a stable value. Each child's tag must match that
value's type. A raw String identifier fits SceneStorage without serializing
view instances. NavigationStack belongs inside a destination; the catalog's
existing stack instead lives in its full-screen presentation.

```swift
// Excerpt
@SceneStorage("foundry.shell.selectedTab") private var selection = ShellTab.home.rawValue
@State private var catalogPresented = false
```

The excerpt comes from the shell linked in [the app walkthrough](../modules/apps/FoundryCatalog/README.md#four-tab-placeholder-shell).
Building the app checks this composition. SceneStorage is a restoration aid,
not durable settings or a guarantee that every termination preserves state.

## Gotchas and evidence limits

The Solid/Glass switch changes Surface rendering. TabView remains
native platform chrome, including its accessibility/material behavior; avoid
layering another custom glass panel around it. Native system widgets own their
internal animation. Simulator observations do not establish physical-device
performance or a comprehensive VoiceOver audit. The app walkthrough records
which paths were actually exercised.

## Used in and related

See [the app walkthrough](../modules/apps/FoundryCatalog/README.md#four-tab-placeholder-shell),
[UI walkthrough](../modules/packages/FoundryUI/README.md), and
[shared lifetime pattern](../../../../notes/patterns/tabs-and-feature-lifetime.md).

## Outlined symbols — 2026-10-08 follow-up

TabView supplies a fill variant for SF Symbols by default. To keep each tab
outlined, set the symbolVariants environment to .none inside its Label:

```swift
// Excerpt: the shell's native tab label.
Label(tab.title, systemImage: tab.symbol)
    .environment(\.symbolVariants, .none)
```

Apple's [SymbolVariants.none documentation](https://developer.apple.com/documentation/swiftui/symbolvariants/none)
explains why symbolVariant(.none) does not clear inherited variants. The explicit
environment override does. Use a base symbol with an actual outline form; the
Account tab now uses person rather than the cropped silhouette. Native tab
highlights, tint and accessible selected state remain platform-owned.
The final simulator screenshot shows all five outlined symbols, including
Account, with Camera selected. Kotlin's existing stroked
tab vectors need no variant override.

# SwiftUI native tabs and shell slots

Claim: adapting TabView preserves native tab behavior while a generic shell
separates passive background from app-owned content and navigation.

Origin/evidence: 2026-10-09, Swift 6.2.3, Xcode 26.2 and iOS 26.2 simulator;
minimum package/app iOS 17. The [Apple TabView documentation](https://developer.apple.com/documentation/swiftui/tabview)
describes selected values and a selection binding. Repository policy narrows this
to one-to-five unique nonempty string IDs. The adapter uses tabItem/tag for the
iOS 17 deployment target rather than requiring newer Tab declarations.

## What and why

TabBar takes an array of passive TabItem icons/labels, a Binding<String> and a
content builder. TabView receives a guarded binding: valid different identity
writes back; repeated or unknown writes do nothing. Native chrome controls
material, tab accessibility and safe areas. Symbol variants are set to none so
supplied outline SF Symbols stay outlined.

AppShell uses generic background/content/navigation values, ZStack and VStack.
Its empty-navigation initializer is constrained to EmptyView: callers using
native TabView do not have to supply a second bottom bar. This is static generic
composition, not a type-erased view tree or a new route controller. Stacks retain
native alignment/modifiers and resolve omitted spacing from the theme.

```swift
// Excerpt: AppShell's content-only initializer constrains the navigation type.
public init(@ViewBuilder background: () -> Background,
            @ViewBuilder content: () -> Content) where Navigation == EmptyView {
    self.init(background: background, content: content, navigation: { EmptyView() })
}
```

## Gotchas and actual use

Put native TabBar in the content slot; native tabs allocate chrome in that region.
Do not add another navigation inset/bar. The host supplies bounded geometry and
owns scrolling, keyboard and feature lifetime. Changing item IDs requires an
admitted selected ID first. Native content lifetime does not preserve feature
values by itself.

[Shared source](../../packages/FoundryUI/Sources/FoundryUI/Components/Navigation/TabBar/TabBar.swift),
[shell source](../../packages/FoundryUI/Sources/FoundryUI/Components/Shells/AppShell/AppShell.swift)
and [hosted tests](../../apps/FoundryCatalog/Tests/ShellComponentTests.swift)
show the concrete implementation. Observed tests cover theme spacing, explicit
spacing/RTL, bounded content/navigation slots, native tab labels/icons and
programmatic selection. All 64 app cases and nine UI package cases pass.
Manual tab gestures, VoiceOver traversal, older OS execution and physical-device
performance were not checked in this slice.

Related: [UI walkthrough](../modules/packages/FoundryUI/README.md#stacks-separators-and-shells),
[app walkthrough](../modules/apps/FoundryCatalog/README.md#shell-primitives-gallery),
[shared ownership](../../../../notes/patterns/shell-chrome-and-feature-lifetime.md).

Next questions: how should tab list changes reconcile a saved identity, and when
should a tablet layout replace bottom tabs with native sidebar navigation?

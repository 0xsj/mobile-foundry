# SwiftUI token environment and scope

SwiftUI's environment supplies visual defaults down a view subtree without
turning theme selection into feature state or a global mutable singleton.

## Origin and versions

Used and inspected 2026-10-08 with Xcode 26.2, Swift 6.2.3, an iOS 17 deployment
target, and iPhone 17 Pro/iOS 26.2 simulator execution. The UI package compiled
on the host and in the app. Manual preview checks established scoped light/dark
rendering, reduced-motion values, and preserved action/position state.

## Mechanism and example

An `EnvironmentKey` declares a value's type and fallback. An `EnvironmentValues`
computed property reads/writes that key through a subscript. The theme writes
the resolved value using `.environment`; descendant views read it through the
`@Environment` property wrapper. A key path such as `\.foundry` identifies the
property to read, rather than copying its current value during initialization.

```swift
// Conceptual: consuming an installed native theme inside a view.
struct SupportingText: View {
    @Environment(\.foundry) private var tokens
    var body: some View {
        Text("Saved on this device")
            .font(tokens.typography.caption)
            .foregroundStyle(tokens.colors.inkSecondary.color)
    }
}
```

The wrapper is supplied by SwiftUI as it evaluates the view. No service call,
task, or manually observed feature store is needed for appearance. The provider
also sets font, foreground, tint, background, and `colorScheme` for its content.
Setting the environment at a subtree lets the preview adapt locally; a window
appearance preference would have broader scope than this catalog needs.

The theme reads the incoming color scheme before overriding its descendants.
A nested provider therefore inherits the parent scheme when no appearance is
specified. Reduction is the OR of the accessibility environment, ancestor token
reduction, and explicit preview flag. The flag can request less motion but
cannot cancel an accessibility request.

Typography uses native semantic Font styles, including a monospaced body role.
This retains Dynamic Type behavior rather than freezing web font sizes. Shape
and spacing use points; a minimum control size must not cap scalable content.

## Gotchas and evidence limits

The key's light fallback allows isolated construction; system-aware production
content should be beneath the theme provider. An appearance override does not
create a new feature identity. The catalog keeps sample state within stable
view structure rather than adding `.id(appearance)`.

Host tests verify token values, contrast pairs, and motion reduction. Simulator
accessibility state showed the counter and position surviving Dark → Light;
a screenshot showed the dark preview beneath light controls. These checks do
not measure iOS hit bounds or establish all Dynamic Type sizes, VoiceOver use,
or physical-device behavior. Token durations govern custom transitions; native
controls keep their framework animation behavior.

## Glass material extension

Added 2026-10-08 with the same Xcode 26.2 / Swift 6.2.3 toolchain. Material style
is another inherited environment value inside the resolved token bundle. The
provider reads `accessibilityReduceTransparency` and combines it with ancestor
and caller reduction. Transparency and motion requests remain independent.

FoundrySurface keeps caller content outside the material switch, changing only
the background view. An availability branch uses regular Liquid Glass on
iOS/macOS 26+, regular Material on earlier supported versions, and a semantic
opaque fill when reduced. `if #available` is a runtime availability check that
also lets the compiler accept a newer SDK API within that branch while keeping
the package's iOS 17/macOS 14 deployment targets. It is not a second feature
implementation or a change to the selected light/dark palette.

The one floating container does not introduce a GlassEffectContainer or morph
identities. Its buttons own interaction; the material is a passive background.
Native glass rendering owns its optical parameters. The shared contract promises
material intent and fallback, not an Android-matching blur radius.
See [glassEffect](https://developer.apple.com/documentation/swiftui/view/glasseffect(_:in:))
and [accessibilityReduceTransparency](https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityreducetransparency).

## Used in and related

The [UI module walkthrough](../modules/packages/FoundryUI/README.md) links the
provider, token families, tests, and app consumer. Compare
[generic content builders](../language/swift-generic-query-state-and-content-builders.md)
and the [shared theme pattern](../../../../notes/patterns/semantic-tokens-and-native-themes.md).
Primary API references: [Environment](https://developer.apple.com/documentation/swiftui/environment)
and [accessibilityReduceMotion](https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityreducemotion).

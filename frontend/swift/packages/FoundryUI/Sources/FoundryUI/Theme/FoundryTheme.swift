import SwiftUI

private struct FoundryTokensKey: EnvironmentKey {
    static let defaultValue = FoundryPreset.v1(appearance: .light)
}

extension EnvironmentValues {
    public var foundry: FoundryTokens {
        get { self[FoundryTokensKey.self] }
        set { self[FoundryTokensKey.self] = newValue }
    }
}

/// Install at composition. Appearance overrides are scoped to this subtree.
public struct FoundryTheme<Content: View>: View {
    @Environment(\.colorScheme) private var systemAppearance
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.accessibilityReduceTransparency) private var systemReduceTransparency
    @Environment(\.foundry) private var parent
    private let appearance: FoundryAppearance?
    private let reduceMotion: Bool
    private let style: FoundryThemeStyle?
    private let reduceTransparency: Bool
    private let content: Content

    public init(appearance: FoundryAppearance? = nil, reduceMotion: Bool = false,
                style: FoundryThemeStyle? = nil, reduceTransparency: Bool = false,
                @ViewBuilder content: () -> Content) {
        self.appearance = appearance
        self.reduceMotion = reduceMotion
        self.style = style
        self.reduceTransparency = reduceTransparency
        self.content = content()
    }

    public var body: some View {
        let selected = appearance ?? (systemAppearance == .dark ? .dark : .light)
        let tokens = FoundryPreset.v1(appearance: selected,
                                    reduceMotion: systemReduceMotion || parent.motion.reduced || reduceMotion,
                                    style: style ?? parent.materials.style,
                                    reduceTransparency: systemReduceTransparency || parent.materials.reduceTransparency || reduceTransparency)
        content
            .environment(\.foundry, tokens)
            .environment(\.colorScheme, selected == .dark ? .dark : .light)
            .font(tokens.typography.body)
            .foregroundStyle(tokens.colors.ink.color)
            .tint(tokens.colors.accent.color)
            .background(tokens.colors.surfaceGround.color)
    }
}

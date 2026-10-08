/// Foundry Studio: porcelain surfaces, blue graphite, and cobalt actions.
public enum FoundryPreset {
    public static func v1(appearance: FoundryAppearance, reduceMotion: Bool = false,
                          style: FoundryThemeStyle = .solid, reduceTransparency: Bool = false) -> FoundryTokens {
        let dark = appearance == .dark
        let colors = FoundryColors(
            surfaceGround: TokenColor(dark ? Palette.graphite950 : Palette.porcelain100),
            surfaceSunk: TokenColor(dark ? Palette.graphite1000 : Palette.porcelain200),
            surfacePanel: TokenColor(dark ? Palette.graphite900 : Palette.porcelain50),
            surfaceRaised: TokenColor(dark ? Palette.graphite800 : Palette.white),
            ink: TokenColor(dark ? Palette.graphite50 : Palette.graphite850),
            inkSecondary: TokenColor(dark ? Palette.graphite300 : Palette.graphite600),
            inkMuted: TokenColor(dark ? Palette.graphite400 : Palette.graphite500),
            line: TokenColor(dark ? Palette.graphite50 : Palette.graphite850, alpha: 0.10),
            lineStrong: TokenColor(dark ? Palette.graphite50 : Palette.graphite850, alpha: 0.20),
            accent: TokenColor(dark ? Palette.cobalt300 : Palette.cobalt700),
            accentTint: TokenColor(dark ? Palette.cobalt300 : Palette.cobalt700, alpha: dark ? 0.12 : 0.08),
            fill: TokenColor(dark ? Palette.cobalt300 : Palette.cobalt700),
            fillInk: TokenColor(dark ? Palette.cobalt950 : Palette.white),
            info: TokenColor(dark ? Palette.sky300 : Palette.sky700),
            warn: TokenColor(dark ? Palette.ochre300 : Palette.ochre700),
            crit: TokenColor(dark ? Palette.vermilion300 : Palette.vermilion700)
        )
        return FoundryTokens(appearance: appearance, colors: colors, space: FoundrySpace(),
                             shape: FoundryShape(), typography: FoundryTypography(), motion: FoundryMotion(reduced: reduceMotion),
                             materials: FoundryMaterials(style: style, reduceTransparency: reduceTransparency))
    }
}

/// Role names remain stable when appearance or a future preset changes.
public struct FoundryColors: Equatable, Sendable {
    public let surfaceGround: TokenColor
    public let surfaceSunk: TokenColor
    public let surfacePanel: TokenColor
    public let surfaceRaised: TokenColor
    public let ink: TokenColor
    public let inkSecondary: TokenColor
    public let inkMuted: TokenColor
    public let line: TokenColor
    public let lineStrong: TokenColor
    public let accent: TokenColor
    public let accentTint: TokenColor
    public let fill: TokenColor
    public let fillInk: TokenColor
    public let info: TokenColor
    public let warn: TokenColor
    public let crit: TokenColor
}

public enum FoundryAppearance: String, CaseIterable, Sendable { case light, dark }

public struct FoundryTokens: Sendable {
    public let appearance: FoundryAppearance
    public let colors: FoundryColors
    public let space: FoundrySpace
    public let shape: FoundryShape
    public let typography: FoundryTypography
    public let motion: FoundryMotion
    public let materials: FoundryMaterials
}

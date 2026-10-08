/// Material choice is independent of the light/dark color mapping.
public enum FoundryThemeStyle: String, CaseIterable, Sendable { case solid, glass }
public enum FoundrySurfaceMaterial: String, Sendable { case solid, glass }

public struct FoundryMaterials: Equatable, Sendable {
    public let style: FoundryThemeStyle
    public let reduceTransparency: Bool
    public let content: FoundrySurfaceMaterial = .solid
    public var floating: FoundrySurfaceMaterial {
        style == .glass && !reduceTransparency ? .glass : .solid
    }

    public init(style: FoundryThemeStyle = .solid, reduceTransparency: Bool = false) {
        self.style = style
        self.reduceTransparency = reduceTransparency
    }
}

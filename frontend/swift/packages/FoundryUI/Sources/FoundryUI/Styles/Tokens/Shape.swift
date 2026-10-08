import CoreGraphics

public struct FoundryShape: Sendable {
    public let radii: [CGFloat] = [4, 8, 12, 16]
    public var panel: CGFloat { radii[3] }
    public let pill: CGFloat = 999
    /// A lower bound; do not use as a fixed text or control height.
    public let minimumInteractive: CGFloat = 44
}

import CoreGraphics

/// Logical points on a four-point rhythm, with a two-point optical step.
public struct FoundrySpace: Sendable {
    public let steps: [CGFloat] = [2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 96]
    public var inline: CGFloat { steps[2] }
    public var stack: CGFloat { steps[3] }
    public var section: CGFloat { steps[6] }
    public var page: CGFloat { steps[5] }
}

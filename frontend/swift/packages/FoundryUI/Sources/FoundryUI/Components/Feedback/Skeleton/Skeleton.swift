import SwiftUI

public enum SkeletonShape: Sendable { case rounded, circle }

/// Decorative loading geometry. Supply a real loading label at the host; this exposes no fake content.
public struct Skeleton: View {
    @Environment(\.foundry) private var tokens
    private let height: CGFloat
    private let width: CGFloat?
    private let shape: SkeletonShape
    private let animated: Bool
    public init(height: CGFloat = 16, width: CGFloat? = nil, shape: SkeletonShape = .rounded, animated: Bool = true) {
        precondition(height.isFinite && height > 0 && (width == nil || (width!.isFinite && width! > 0)))
        self.height = height; self.width = width; self.shape = shape; self.animated = animated
    }
    public var body: some View {
        Group {
            if animated && !tokens.motion.reduced {
                placeholder.phaseAnimator([false, true]) { content, phase in content.opacity(phase ? 0.45 : 0.85) }
                    animation: { _ in .easeInOut(duration: 0.9) }
            } else { placeholder }
        }.accessibilityHidden(true).allowsHitTesting(false)
    }
    private var placeholder: some View {
        RoundedRectangle(cornerRadius: shape == .circle ? height / 2 : tokens.shape.radii[1])
            .fill(tokens.colors.line.color).frame(width: shape == .circle ? height : width, height: height)
    }
}

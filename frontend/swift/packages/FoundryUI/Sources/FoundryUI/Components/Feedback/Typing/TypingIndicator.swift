import SwiftUI

/// Caller decides whether typing is present. Dots are decorative; the supplied label is always visible.
public struct TypingIndicator: View {
    @Environment(\.foundry) private var t
    private let label: String
    private let animated: Bool
    public init(_ label: String, animated: Bool = true) { self.label = label; self.animated = animated }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            if animated && !t.motion.reduced {
                dots.phaseAnimator([false, true]) { content, phase in content.opacity(phase ? 0.4 : 1) }
                    animation: { _ in .easeInOut(duration: 0.7) }
            } else { dots }
            Text(label).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
        }.accessibilityElement(children: .ignore).accessibilityLabel(label).allowsHitTesting(false)
    }
    private var dots: some View {
        HStack(spacing: 4) { ForEach(0..<3) { _ in Circle().fill(t.colors.accent.color).frame(width: 6, height: 6) } }
            .accessibilityHidden(true)
    }
}

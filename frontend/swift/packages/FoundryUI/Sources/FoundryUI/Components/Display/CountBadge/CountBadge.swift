import SwiftUI

/// Passive caller-formatted count and complete narration. The host owns zero visibility, capping and pluralization.
public struct CountBadge: View {
    @Environment(\.foundry) private var t
    private let text: String
    private let accessibilityLabel: String
    public init(_ text: String, accessibilityLabel: String) {
        self.text = text; self.accessibilityLabel = accessibilityLabel
    }
    public var body: some View {
        Text(text).font(t.typography.caption.weight(.semibold)).foregroundStyle(t.colors.accent.color)
            .padding(.horizontal, t.space.inline).padding(.vertical, t.space.steps[1])
            .background(t.colors.accentTint.color, in: Capsule())
            .accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
    }
}

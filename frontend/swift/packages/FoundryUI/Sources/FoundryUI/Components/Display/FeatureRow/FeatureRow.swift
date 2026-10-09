import SwiftUI

/// Passive feature/availability copy. The mark is decorative; supply complete narration rather than relying on it.
public struct FeatureRow: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String?
    private let stateLabel: String
    private let included: Bool
    private let accessibilityLabel: String
    public init(_ title: String, detail: String? = nil, stateLabel: String, included: Bool, accessibilityLabel: String) {
        self.title = title; self.detail = detail; self.stateLabel = stateLabel
        self.included = included; self.accessibilityLabel = accessibilityLabel
    }
    public var body: some View {
        HStack(alignment: .top, spacing: t.space.inline) {
            Image(systemName: included ? "checkmark.circle" : "minus.circle").accessibilityHidden(true)
                .foregroundStyle(included ? t.colors.accent.color : t.colors.inkSecondary.color)
            VStack(alignment: .leading, spacing: t.space.steps[1]) {
                Text(title).font(t.typography.body)
                Text(stateLabel).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
    }
}

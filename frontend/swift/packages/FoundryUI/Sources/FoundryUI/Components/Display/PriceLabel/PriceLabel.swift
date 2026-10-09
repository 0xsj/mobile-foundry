import SwiftUI

/// Supplied price copy with optional previous price and detail. Formatting/comparison policy belongs to the host.
public struct PriceLabel: View {
    @Environment(\.foundry) private var t
    private let value: String
    private let comparison: String?
    private let detail: String?
    private let accessibilityLabel: String
    public init(_ value: String, comparison: String? = nil, detail: String? = nil, accessibilityLabel: String) {
        self.value = value; self.comparison = comparison; self.detail = detail; self.accessibilityLabel = accessibilityLabel
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(value).font(t.typography.heading).foregroundStyle(t.colors.ink.color)
            if let comparison { Text(comparison).font(t.typography.body).strikethrough().foregroundStyle(t.colors.inkSecondary.color) }
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
    }
}

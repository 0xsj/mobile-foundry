import SwiftUI

/// Passive supplied usage meaning. Finite fractions clamp to 0...1; nil/nonfinite hides the decorative bar, not a loading state.
public struct UsageMeter: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let value: String
    private let detail: String?
    private let fraction: Double?
    private let accessibilityLabel: String
    public init(_ title: String, value: String, detail: String? = nil, fraction: Double? = nil, accessibilityLabel: String) {
        self.title = title; self.value = value; self.detail = detail; self.accessibilityLabel = accessibilityLabel
        self.fraction = fraction.flatMap { $0.isFinite ? min(1, max(0, $0)) : nil }
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label)
            Text(value).font(t.typography.heading)
            if let fraction { ProgressView(value: fraction).tint(t.colors.accent.color) }
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel)
    }
}

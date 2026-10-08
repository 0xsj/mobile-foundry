import SwiftUI

/// Preformatted values and trends; no calculation, implied direction or automatic count animation.
public struct StatCard: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let value: String
    private let detail: String?
    private let trend: String?
    private let tone: MessageTone
    private let role: SurfaceRole
    public init(_ title: String, value: String, detail: String? = nil, trend: String? = nil,
                tone: MessageTone = .neutral, role: SurfaceRole = .content) {
        self.title = title; self.value = value; self.detail = detail; self.trend = trend; self.tone = tone; self.role = role
    }
    public var body: some View {
        Card(role) {
            Text(title).font(tokens.typography.label)
            Text(value).font(tokens.typography.title).monospacedDigit()
            if let trend { Text(trend).font(tokens.typography.caption).foregroundStyle(tone.color(in: tokens)) }
            if let detail { Text(detail).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color) }
        }.accessibilityElement(children: .combine)
    }
}

import SwiftUI

public enum TrendDirection: Sendable { case up, down, steady }
/// Caller-formatted comparison. Direction is visual; tone never infers whether an increase is desirable.
public struct TrendBadge: View {
    @Environment(\.foundry) private var t
    private let label: String
    private let direction: TrendDirection
    private let tone: MessageTone
    public init(_ label: String, direction: TrendDirection, tone: MessageTone = .neutral) {
        self.label = label; self.direction = direction; self.tone = tone
    }
    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: t.space.inline) {
            Image(systemName: direction == .up ? "arrow.up.right" : direction == .down ? "arrow.down.right" : "arrow.right")
                .accessibilityHidden(true)
            Text(label).fixedSize(horizontal: false, vertical: true)
        }.font(t.typography.caption).foregroundStyle(tone.color(in: t))
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
    }
}

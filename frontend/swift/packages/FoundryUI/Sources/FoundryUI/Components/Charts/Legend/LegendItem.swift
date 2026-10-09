import SwiftUI

public enum LegendMark: Sendable { case dot, line, square }
/// Passive series label with a decorative mark. Supply meaningful copy beyond color.
public struct LegendItem: View {
    @Environment(\.foundry) private var t
    private let label: String
    private let color: Color?
    private let mark: LegendMark
    public init(_ label: String, color: Color? = nil, mark: LegendMark = .dot) {
        self.label = label; self.color = color; self.mark = mark
    }
    public var body: some View {
        HStack(spacing: t.space.inline) {
            RoundedRectangle(cornerRadius: mark == .dot ? 6 : mark == .line ? 2 : 0)
                .fill(color ?? t.colors.accent.color).frame(width: mark == .line ? 24 : 12, height: mark == .line ? 4 : 12)
                .accessibilityHidden(true)
            Text(label).font(t.typography.caption).fixedSize(horizontal: false, vertical: true)
        }
    }
}

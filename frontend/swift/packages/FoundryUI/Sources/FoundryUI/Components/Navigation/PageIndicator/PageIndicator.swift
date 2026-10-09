import SwiftUI

/// Passive position summary for a small carousel. The caller owns selection and localized copy.
public struct PageIndicator: View {
    @Environment(\.foundry) private var t
    private let count: Int
    private let selected: Int
    private let label: String
    public init(count: Int, selected: Int, label: String) {
        precondition((1...20).contains(count) && (0..<count).contains(selected))
        self.count = count; self.selected = selected; self.label = label
    }
    public var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { index in
                Capsule().fill(index == selected ? t.colors.accent.color : t.colors.lineStrong.color)
                    .frame(width: index == selected ? 18 : 6, height: 6)
            }
        }.accessibilityElement(children: .ignore).accessibilityLabel(label)
    }
}

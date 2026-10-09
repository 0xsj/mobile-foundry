import SwiftUI

/// A controlled integer rating with native choice actions. Zero means unrated; clearing is a caller action.
public struct RatingField: View {
    @Environment(\.foundry) private var t
    private let title: String
    @Binding private var value: Int
    private let maximum: Int
    private let valueLabel: String
    private let optionLabel: (Int) -> String
    private let enabled: Bool
    public init(_ title: String, value: Binding<Int>, valueLabel: String, maximum: Int = 5,
                enabled: Bool = true, optionLabel: @escaping (Int) -> String) {
        precondition((1...10).contains(maximum) && (0...maximum).contains(value.wrappedValue))
        self.title = title; self._value = value; self.valueLabel = valueLabel; self.maximum = maximum
        self.enabled = enabled; self.optionLabel = optionLabel
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label)
            Text(valueLabel).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: t.shape.minimumInteractive), spacing: 0)], alignment: .leading, spacing: 0) {
                ForEach(1...maximum, id: \.self) { score in
                    Button { value = score } label: {
                        Image(systemName: score <= value ? "star.fill" : "star")
                            .font(.system(size: 20)).frame(maxWidth: .infinity, minHeight: t.shape.minimumInteractive)
                            .contentShape(Rectangle())
                    }.buttonStyle(.plain).foregroundStyle(t.colors.accent.color)
                        .accessibilityLabel(optionLabel(score))
                        .accessibilityAddTraits(value == score ? .isSelected : [])
                        .disabled(!enabled)
                }
            }.opacity(enabled ? 1 : 0.5)
        }
    }
}

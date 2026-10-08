import SwiftUI

/// steps counts intermediate stops; zero is continuous. Supply bounded, finite values.
public struct ValueSlider: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    @Binding private var value: Double
    private let range: ClosedRange<Double>
    private let steps: Int
    private let valueLabel: String
    private let enabled: Bool
    private let onEditingChanged: (Bool) -> Void
    public init(_ title: String, value: Binding<Double>, in range: ClosedRange<Double> = 0...1,
                steps: Int = 0, valueLabel: String, enabled: Bool = true,
                onEditingChanged: @escaping (Bool) -> Void = { _ in }) {
        precondition(range.lowerBound.isFinite && range.upperBound.isFinite && range.lowerBound < range.upperBound)
        precondition(value.wrappedValue.isFinite && range.contains(value.wrappedValue) && steps >= 0)
        self.title = title; self._value = value; self.range = range; self.steps = steps
        self.valueLabel = valueLabel; self.enabled = enabled; self.onEditingChanged = onEditingChanged
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
            Text(title).font(tokens.typography.label)
            Text(valueLabel).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            slider.accessibilityLabel(title).accessibilityValue(valueLabel).disabled(!enabled)
        }
    }
    @ViewBuilder private var slider: some View {
        if steps == 0 { Slider(value: $value, in: range, onEditingChanged: onEditingChanged) }
        else { Slider(value: $value, in: range, step: (range.upperBound - range.lowerBound) / Double(steps + 1), onEditingChanged: onEditingChanged) }
    }
}

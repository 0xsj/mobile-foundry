import SwiftUI

/// Bounded integer changes, clamped at endpoints. Copy and committed value belong to the caller.
public struct ValueStepper: View {
    @Environment(\.foundry) private var t
    @Binding private var value: Int
    private let title: String
    private let valueLabel: String
    private let decreaseLabel: String
    private let increaseLabel: String
    private let range: ClosedRange<Int>
    private let step: Int
    private let enabled: Bool
    public init(_ title: String, value: Binding<Int>, valueLabel: String,
                decreaseLabel: String, increaseLabel: String, range: ClosedRange<Int> = 0...10,
                step: Int = 1, enabled: Bool = true) {
        precondition(range.contains(value.wrappedValue) && step > 0)
        self.title = title; self._value = value; self.valueLabel = valueLabel
        self.decreaseLabel = decreaseLabel; self.increaseLabel = increaseLabel
        self.range = range; self.step = step; self.enabled = enabled
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label)
            HStack(spacing: t.space.stack) {
                ActionButton(variant: .secondary, enabled: enabled && value > range.lowerBound, action: {
                    let next = value.subtractingReportingOverflow(step)
                    value = next.overflow ? range.lowerBound : max(range.lowerBound, next.partialValue)
                }) { Image(systemName: "minus") }.accessibilityLabel(decreaseLabel)
                Text(valueLabel).frame(maxWidth: .infinity).multilineTextAlignment(.center)
                ActionButton(variant: .secondary, enabled: enabled && value < range.upperBound, action: {
                    let next = value.addingReportingOverflow(step)
                    value = next.overflow ? range.upperBound : min(range.upperBound, next.partialValue)
                }) { Image(systemName: "plus") }.accessibilityLabel(increaseLabel)
            }
        }.accessibilityElement(children: .contain)
    }
}

import SwiftUI

public struct ChartBar: Identifiable, Sendable {
    public let id: String
    public let label: String
    public let value: Double
    public let valueLabel: String
    public init(id: String, label: String, value: Double, valueLabel: String) {
        precondition(value.isFinite && value >= 0)
        self.id = id; self.label = label; self.value = value; self.valueLabel = valueLabel
    }
}
/// Small eager nonnegative bars with an explicit positive common maximum and caller-formatted values.
public struct BarChart: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let bars: [ChartBar]
    private let maximum: Double
    private let color: Color?
    public init(_ title: String, bars: [ChartBar], maximum: Double, color: Color? = nil) {
        precondition(maximum.isFinite && maximum > 0 && bars.allSatisfy { $0.value <= maximum })
        precondition(Set(bars.map(\.id)).count == bars.count)
        self.title = title; self.bars = bars; self.maximum = maximum; self.color = color
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            ForEach(bars) { bar in
                VStack(alignment: .leading, spacing: t.space.inline) {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline) { Text(bar.label); Spacer(minLength: t.space.inline); Text(bar.valueLabel).monospacedDigit() }
                            .fixedSize(horizontal: true, vertical: false)
                        VStack(alignment: .leading, spacing: t.space.inline) { Text(bar.label); Text(bar.valueLabel).monospacedDigit() }
                    }.font(t.typography.caption)
                    GeometryReader { geometry in
                        Capsule().fill(t.colors.line.color)
                        Capsule().fill(color ?? t.colors.accent.color).frame(width: geometry.size.width * (bar.value / maximum))
                    }.frame(height: 12).accessibilityHidden(true)
                }.accessibilityElement(children: .ignore).accessibilityLabel(bar.label).accessibilityValue(bar.valueLabel)
            }
        }.accessibilityElement(children: .ignore).accessibilityLabel(title)
            .accessibilityValue(bars.map { "\($0.label): \($0.valueLabel)" }.joined(separator: "; "))
    }
}

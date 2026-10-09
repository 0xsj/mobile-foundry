import SwiftUI

/// Determinate ring. Finite fractions clamp to 0...1; caller supplies readable value and meaning.
public struct ProgressRing: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var textSize
    private let label: String
    private let valueLabel: String
    private let fraction: Double
    private let diameter: CGFloat
    private let thickness: CGFloat
    private let color: Color?
    public init(_ label: String, fraction: Double, valueLabel: String, diameter: CGFloat = 120, thickness: CGFloat = 8, color: Color? = nil) {
        precondition(fraction.isFinite && diameter.isFinite && thickness.isFinite && diameter > 0 && thickness > 0 && thickness < diameter / 2)
        self.label = label; self.valueLabel = valueLabel; self.fraction = min(1, max(0, fraction))
        self.diameter = diameter; self.thickness = thickness; self.color = color
    }
    public var body: some View {
        VStack(spacing: t.space.inline) {
            ZStack {
                Circle().stroke(t.colors.line.color, lineWidth: thickness)
                Circle().trim(from: 0, to: fraction).stroke(color ?? t.colors.accent.color, style: StrokeStyle(lineWidth: thickness, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                if !textSize.isAccessibilitySize { Text(valueLabel).font(t.typography.label).monospacedDigit().multilineTextAlignment(.center).padding(thickness * 2) }
            }.padding(thickness / 2).frame(width: diameter, height: diameter).accessibilityHidden(true)
            if textSize.isAccessibilitySize { Text(valueLabel).font(t.typography.label).monospacedDigit() }
            Text(label).font(t.typography.caption).multilineTextAlignment(.center)
        }.accessibilityElement(children: .ignore).accessibilityLabel(label).accessibilityValue(valueLabel)
    }
}

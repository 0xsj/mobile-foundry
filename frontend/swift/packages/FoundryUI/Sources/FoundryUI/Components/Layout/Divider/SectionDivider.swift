import SwiftUI

public enum DividerAxis: Sendable { case horizontal, vertical }

/// Decorative theme separator. Vertical separators require a bounded host height; neither axis supplies meaning or interaction.
public struct SectionDivider: View {
    @Environment(\.foundry) private var t
    private let axis: DividerAxis
    private let inset: CGFloat
    private let thickness: CGFloat
    private let color: Color?
    public init(axis: DividerAxis = .horizontal, inset: CGFloat = 0, thickness: CGFloat = 1, color: Color? = nil) {
        precondition(inset.isFinite && inset >= 0 && thickness.isFinite && thickness > 0)
        self.axis = axis; self.inset = inset; self.thickness = thickness; self.color = color
    }
    public var body: some View {
        Group {
            if axis == .horizontal { Rectangle().fill(color ?? t.colors.line.color).frame(height: thickness).frame(maxWidth: .infinity).padding(.horizontal, inset) }
            else { Rectangle().fill(color ?? t.colors.line.color).frame(width: thickness).frame(maxHeight: .infinity).padding(.vertical, inset) }
        }.accessibilityHidden(true)
    }
}

import SwiftUI

/// Eager, non-scrolling grid for small compositions. Use native lazy collections for long feeds.
/// Children retain their native identity; width and text size determine the column count.
public struct AdaptiveGrid<Content: View>: View {
    @Environment(\.foundry) private var t
    @ScaledMetric private var scaledMinimum: CGFloat
    private let maximumColumns: Int
    private let spacing: CGFloat?
    private let content: Content

    public init(minimumItemWidth: CGFloat = 160, maximumColumns: Int = 3, spacing: CGFloat? = nil,
                @ViewBuilder content: () -> Content) {
        precondition(minimumItemWidth.isFinite && minimumItemWidth > 0 && maximumColumns > 0)
        precondition(spacing == nil || (spacing!.isFinite && spacing! >= 0))
        self._scaledMinimum = ScaledMetric(wrappedValue: minimumItemWidth, relativeTo: .body)
        self.maximumColumns = maximumColumns; self.spacing = spacing; self.content = content()
    }
    public var body: some View {
        GridArrangement(minimum: scaledMinimum, maximumColumns: maximumColumns,
                        gap: spacing ?? t.space.stack) { content }
    }
}

private struct GridArrangement: Layout {
    let minimum: CGFloat
    let maximumColumns: Int
    let gap: CGFloat

    private func measurements(width: CGFloat, subviews: Subviews) -> (columns: Int, cell: CGFloat, rows: [CGFloat]) {
        let columns = max(1, min(maximumColumns, Int((width + gap) / (minimum + gap))))
        let cell = max(0, (width - CGFloat(columns - 1) * gap) / CGFloat(columns))
        var rows: [CGFloat] = []
        for (index, view) in subviews.enumerated() {
            if index % columns == 0 { rows.append(0) }
            rows[index / columns] = max(rows[index / columns], view.sizeThatFits(.init(width: cell, height: nil)).height)
        }
        return (columns, cell, rows)
    }
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width.flatMap { $0.isFinite ? max(0, $0) : nil } ?? minimum
        let m = measurements(width: width, subviews: subviews)
        return CGSize(width: width, height: m.rows.reduce(0, +) + CGFloat(max(0, m.rows.count - 1)) * gap)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let m = measurements(width: bounds.width, subviews: subviews)
        var y = bounds.minY
        for (index, view) in subviews.enumerated() {
            // SwiftUI mirrors native Layout placement automatically in RTL.
            let column = index % m.columns
            view.place(at: CGPoint(x: bounds.minX + CGFloat(column) * (m.cell + gap), y: y), anchor: .topLeading,
                       proposal: .init(width: m.cell, height: nil))
            if index % m.columns == m.columns - 1 { y += m.rows[index / m.columns] + gap }
        }
    }
}

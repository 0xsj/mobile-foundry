import SwiftUI

/// Small eager compositions with intrinsic child widths. Scrolling and stable child identity belong to the host.
public struct WrapLayout<Content: View>: View {
    @Environment(\.foundry) private var t
    private let horizontalSpacing: CGFloat?
    private let verticalSpacing: CGFloat?
    private let content: Content
    public init(horizontalSpacing: CGFloat? = nil, verticalSpacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        precondition(horizontalSpacing == nil || (horizontalSpacing!.isFinite && horizontalSpacing! >= 0))
        precondition(verticalSpacing == nil || (verticalSpacing!.isFinite && verticalSpacing! >= 0))
        self.horizontalSpacing = horizontalSpacing; self.verticalSpacing = verticalSpacing; self.content = content()
    }
    public var body: some View {
        WrapArrangement(horizontal: horizontalSpacing ?? t.space.inline, vertical: verticalSpacing ?? t.space.inline) { content }
    }
}
private struct WrapArrangement: Layout {
    let horizontal: CGFloat
    let vertical: CGFloat
    private struct Item { let origin: CGPoint; let size: CGSize }
    private func arrange(width: CGFloat, subviews: Subviews) -> (items: [Item], height: CGFloat) {
        var items: [Item] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for view in subviews {
            let ideal = view.sizeThatFits(.unspecified)
            let childWidth = min(width, max(0, ideal.width))
            let size = view.sizeThatFits(.init(width: childWidth, height: nil))
            if x > 0 && x + size.width > width {
                x = 0; y += rowHeight + vertical; rowHeight = 0
            }
            items.append(Item(origin: CGPoint(x: x, y: y), size: size))
            x += size.width + horizontal; rowHeight = max(rowHeight, size.height)
        }
        return (items, items.isEmpty ? 0 : y + rowHeight)
    }
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ideal = subviews.map { $0.sizeThatFits(.unspecified).width }.reduce(0, +) + CGFloat(max(0, subviews.count - 1)) * horizontal
        let width = proposal.width.flatMap { $0.isFinite ? max(0, $0) : nil } ?? ideal
        return CGSize(width: width, height: arrange(width: width, subviews: subviews).height)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(width: bounds.width, subviews: subviews)
        for (view, item) in zip(subviews, result.items) {
            view.place(at: CGPoint(x: bounds.minX + item.origin.x, y: bounds.minY + item.origin.y),
                       anchor: .topLeading, proposal: .init(item.size))
        }
    }
}

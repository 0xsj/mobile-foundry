import SwiftUI

/// Eager native stack using theme stack spacing by default. No scrolling or feature state.
public struct VerticalStack<Content: View>: View {
    @Environment(\.foundry) private var t
    private let alignment: HorizontalAlignment
    private let spacing: CGFloat?
    private let content: Content
    public init(alignment: HorizontalAlignment = .leading, spacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        precondition(spacing == nil || (spacing!.isFinite && spacing! >= 0))
        self.alignment = alignment; self.spacing = spacing; self.content = content()
    }
    public var body: some View { VStack(alignment: alignment, spacing: spacing ?? t.space.stack) { content } }
}

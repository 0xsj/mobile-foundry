import SwiftUI

/// Eager native stack using theme inline spacing by default. Host chooses bounds and wrapping alternatives.
public struct HorizontalStack<Content: View>: View {
    @Environment(\.foundry) private var t
    private let alignment: VerticalAlignment
    private let spacing: CGFloat?
    private let content: Content
    public init(alignment: VerticalAlignment = .center, spacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        precondition(spacing == nil || (spacing!.isFinite && spacing! >= 0))
        self.alignment = alignment; self.spacing = spacing; self.content = content()
    }
    public var body: some View { HStack(alignment: alignment, spacing: spacing ?? t.space.inline) { content } }
}

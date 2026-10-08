import SwiftUI

/// Centered readable bounds including page insets. The caller owns scrolling and safe areas.
public struct ContentContainer<Content: View>: View {
    @Environment(\.foundry) private var t
    private let maximumWidth: CGFloat
    private let inset: CGFloat?
    private let content: Content
    public init(maximumWidth: CGFloat = 720, inset: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        precondition(maximumWidth.isFinite && maximumWidth > 0)
        precondition(inset == nil || (inset!.isFinite && inset! >= 0))
        self.maximumWidth = maximumWidth; self.inset = inset; self.content = content()
    }
    public var body: some View {
        content.frame(maxWidth: .infinity, alignment: .leading)
            .padding(inset ?? t.space.page)
            .frame(maxWidth: maximumWidth, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .top)
    }
}

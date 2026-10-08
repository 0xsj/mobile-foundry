import SwiftUI

/// Width-led media bounds. Children choose image fitting; overflowing artwork is clipped.
public struct MediaFrame<Content: View>: View {
    private let ratio: CGFloat
    private let content: Content
    public init(ratio: CGFloat = 16 / 9, @ViewBuilder content: () -> Content) {
        precondition(ratio.isFinite && ratio > 0)
        self.ratio = ratio; self.content = content()
    }
    public var body: some View {
        Color.clear.aspectRatio(ratio, contentMode: .fit)
            .overlay { content.frame(maxWidth: .infinity, maxHeight: .infinity) }
            .clipped()
    }
}

import SwiftUI

/// A media frame, supplied metadata and independent actions. No implied tap, image loading or media model.
public struct MediaTile<Artwork: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let ratio: CGFloat
    private let artwork: Artwork
    private let actions: Actions
    public init(_ title: String, subtitle: String? = nil, ratio: CGFloat = 4 / 3,
                @ViewBuilder artwork: () -> Artwork, @ViewBuilder actions: () -> Actions) {
        precondition(ratio.isFinite && ratio > 0)
        self.title = title; self.subtitle = subtitle; self.ratio = ratio; self.artwork = artwork(); self.actions = actions()
    }
    public var body: some View {
        Card {
            MediaFrame(ratio: ratio) { artwork }
            VStack(alignment: .leading, spacing: t.space.inline) {
                Text(title).font(t.typography.label)
                if let subtitle { Text(subtitle).foregroundStyle(t.colors.inkSecondary.color) }
            }.accessibilityElement(children: .combine)
            actions
        }
    }
}
extension MediaTile where Actions == EmptyView {
    public init(_ title: String, subtitle: String? = nil, ratio: CGFloat = 4 / 3, @ViewBuilder artwork: () -> Artwork) {
        self.init(title, subtitle: subtitle, ratio: ratio, artwork: artwork) { EmptyView() }
    }
}

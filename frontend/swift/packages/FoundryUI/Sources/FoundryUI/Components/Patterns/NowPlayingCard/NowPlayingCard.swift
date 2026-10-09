import SwiftUI

/// Passive supplied media identity/artwork and independent timeline, controls and actions. No engine or clock ownership.
public struct NowPlayingCard<Artwork: View, Timeline: View, Controls: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    private let title: String
    private let detail: String?
    private let accessibilityLabel: String
    private let artworkSize: CGFloat
    private let artwork: Artwork
    private let timeline: Timeline
    private let controls: Controls
    private let actions: Actions
    public init(_ title: String, detail: String? = nil, accessibilityLabel: String, artworkSize: CGFloat = 80,
                @ViewBuilder artwork: () -> Artwork, @ViewBuilder timeline: () -> Timeline,
                @ViewBuilder controls: () -> Controls, @ViewBuilder actions: () -> Actions) {
        precondition(artworkSize.isFinite && artworkSize > 0)
        self.title = title; self.detail = detail; self.accessibilityLabel = accessibilityLabel; self.artworkSize = artworkSize
        self.artwork = artwork(); self.timeline = timeline(); self.controls = controls(); self.actions = actions()
    }
    public var body: some View {
        Card {
            Group {
                if typeSize.isAccessibilitySize { VStack(alignment: .leading, spacing: t.space.inline) { cover; identity } }
                else { HStack(alignment: .top, spacing: t.space.inline) { cover; identity } }
            }.accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel).accessibilityAddTraits(.isHeader)
            timeline
            controls
            actions
        }
    }
    private var cover: some View {
        MediaFrame(ratio: 1) { artwork }.frame(width: artworkSize, height: artworkSize)
            .clipShape(RoundedRectangle(cornerRadius: t.shape.radii[1]))
    }
    private var identity: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.heading)
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

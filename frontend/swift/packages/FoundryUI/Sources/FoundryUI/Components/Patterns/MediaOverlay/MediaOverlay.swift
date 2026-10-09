import SwiftUI

/// Decorative artwork behind a bottom scrim and independent overlay content. Put media meaning in the overlay.
/// The host supplies bounded layout/clipping and readable action surfaces; artwork must contain no controls.
public struct MediaOverlay<Artwork: View, Overlay: View>: View {
    @Environment(\.foundry) private var t
    private let artwork: Artwork
    private let overlay: Overlay
    private let scrimOpacity: Double
    public init(scrimOpacity: Double = 0.75, @ViewBuilder artwork: () -> Artwork, @ViewBuilder overlay: () -> Overlay) {
        precondition(scrimOpacity.isFinite && (0...1).contains(scrimOpacity))
        self.scrimOpacity = scrimOpacity; self.artwork = artwork(); self.overlay = overlay()
    }
    public var body: some View {
        ZStack(alignment: .bottomLeading) {
            artwork.frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityHidden(true)
            LinearGradient(colors: [.clear, .black.opacity(scrimOpacity)], startPoint: .center, endPoint: .bottom)
                .allowsHitTesting(false).accessibilityHidden(true)
            overlay.foregroundStyle(.white).padding(t.space.stack).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

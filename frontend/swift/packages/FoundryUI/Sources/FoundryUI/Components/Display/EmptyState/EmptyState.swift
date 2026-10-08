import SwiftUI

public struct EmptyState<Artwork: View, Actions: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let message: String
    private let artwork: Artwork
    private let actions: Actions
    public init(_ title: String, message: String, @ViewBuilder artwork: () -> Artwork,
                @ViewBuilder actions: () -> Actions) {
        self.title = title; self.message = message; self.artwork = artwork(); self.actions = actions()
    }
    public var body: some View {
        VStack(spacing: tokens.space.stack) {
            artwork.accessibilityHidden(true)
            Text(title).font(tokens.typography.heading).accessibilityAddTraits(.isHeader)
            Text(message).foregroundStyle(tokens.colors.inkSecondary.color)
            actions
        }.multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(tokens.space.section)
    }
}

extension EmptyState where Artwork == EmptyView, Actions == EmptyView {
    public init(_ title: String, message: String) {
        self.init(title, message: message, artwork: { EmptyView() }, actions: { EmptyView() })
    }
}

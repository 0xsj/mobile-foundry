import SwiftUI

/// One bounded page with scrolling artwork/copy/content and a separate action region.
/// The caller owns step identity, validation, focus and transitions.
public struct OnboardingPage<Artwork: View, Content: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let message: String
    private let artwork: Artwork
    private let content: Content
    private let actions: Actions
    public init(_ title: String, message: String, @ViewBuilder artwork: () -> Artwork,
                @ViewBuilder content: () -> Content, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.message = message
        self.artwork = artwork(); self.content = content(); self.actions = actions()
    }
    public var body: some View {
        DetailShell(header: { EmptyView() }, content: {
            ScrollView {
                ContentContainer {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        artwork
                        PageHeader(title, subtitle: message)
                        content
                    }
                }
            }
        }, actions: { ContentContainer { actions } })
    }
}

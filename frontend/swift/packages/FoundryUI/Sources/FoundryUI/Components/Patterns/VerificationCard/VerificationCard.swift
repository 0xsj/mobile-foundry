import SwiftUI

/// Supplied delivery identity and passive artwork with independent content/status/action slots. No auth or delivery work.
public struct VerificationCard<Artwork: View, Content: View, Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    private let title: String
    private let destination: String
    private let accessibilityLabel: String
    private let artwork: Artwork
    private let content: Content
    private let status: Status
    private let actions: Actions
    public init(_ title: String, destination: String, accessibilityLabel: String,
                @ViewBuilder artwork: () -> Artwork, @ViewBuilder content: () -> Content,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.destination = destination; self.accessibilityLabel = accessibilityLabel
        self.artwork = artwork(); self.content = content(); self.status = status(); self.actions = actions()
    }
    public var body: some View {
        Card {
            Group {
                if typeSize.isAccessibilitySize { VStack(alignment: .leading, spacing: t.space.inline) { artwork; identity } }
                else { HStack(alignment: .top, spacing: t.space.inline) { artwork; identity } }
            }.accessibilityElement(children: .ignore).accessibilityLabel(accessibilityLabel).accessibilityAddTraits(.isHeader)
            content
            status
            actions
        }
    }
    private var identity: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.heading)
            Text(destination).font(t.typography.body).foregroundStyle(t.colors.inkSecondary.color)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

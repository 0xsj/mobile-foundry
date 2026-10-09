import SwiftUI

/// One native action. Leading artwork is passive; routes and unread copy belong to the caller.
public struct ConversationRow<Leading: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let preview: String
    private let timestamp: String
    private let unreadLabel: String?
    private let enabled: Bool
    private let onOpen: () -> Void
    private let leading: Leading
    public init(_ title: String, preview: String, timestamp: String, unreadLabel: String? = nil,
                enabled: Bool = true, onOpen: @escaping () -> Void, @ViewBuilder leading: () -> Leading) {
        self.title = title; self.preview = preview; self.timestamp = timestamp; self.unreadLabel = unreadLabel
        self.enabled = enabled; self.onOpen = onOpen; self.leading = leading()
    }
    public var body: some View {
        Button(action: onOpen) {
            ListRow(title, subtitle: preview, leading: { leading.accessibilityHidden(true) }, trailing: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    Text(timestamp).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                    if let unreadLabel { Badge(unreadLabel, tone: .info) }
                }
            }).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled)
            .accessibilityElement(children: .combine)
    }
}

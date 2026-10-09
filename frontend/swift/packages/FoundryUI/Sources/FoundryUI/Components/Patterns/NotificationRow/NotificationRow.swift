import SwiftUI

/// One native open action, unread emphasis and independent sibling actions. Artwork is passive; all copy is supplied.
public struct NotificationRow<Leading: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let message: String
    private let timeLabel: String
    private let stateLabel: String
    private let isUnread: Bool
    private let accessibilityLabel: String
    private let enabled: Bool
    private let onOpen: () -> Void
    private let leading: Leading
    private let actions: Actions
    public init(_ title: String, message: String, timeLabel: String, stateLabel: String, isUnread: Bool,
                accessibilityLabel: String, enabled: Bool = true, onOpen: @escaping () -> Void,
                @ViewBuilder leading: () -> Leading, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.message = message; self.timeLabel = timeLabel; self.stateLabel = stateLabel
        self.isUnread = isUnread; self.accessibilityLabel = accessibilityLabel; self.enabled = enabled
        self.onOpen = onOpen; self.leading = leading(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Button { if enabled { onOpen() } } label: {
                HStack(alignment: .top, spacing: t.space.stack) {
                    leading.accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: t.space.inline) {
                        Text(title).font(t.typography.body.weight(isUnread ? .semibold : .regular))
                        Text(message).font(t.typography.body).foregroundStyle(t.colors.inkSecondary.color)
                        Text(timeLabel).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                        Text(stateLabel).font(t.typography.caption).foregroundStyle(isUnread ? t.colors.accent.color : t.colors.inkSecondary.color)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.frame(maxWidth: .infinity, minHeight: t.shape.minimumInteractive, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled)
                .accessibilityElement(children: .combine).accessibilityLabel(accessibilityLabel)
            actions
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension NotificationRow where Leading == EmptyView, Actions == EmptyView {
    public init(_ title: String, message: String, timeLabel: String, stateLabel: String, isUnread: Bool,
                accessibilityLabel: String, enabled: Bool = true, onOpen: @escaping () -> Void) {
        self.init(title, message: message, timeLabel: timeLabel, stateLabel: stateLabel, isUnread: isUnread,
                  accessibilityLabel: accessibilityLabel, enabled: enabled, onOpen: onOpen, leading: { EmptyView() }, actions: { EmptyView() })
    }
}

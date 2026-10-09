import SwiftUI

/// Supplied schedule copy and independent status/action slots. No date arithmetic, route or event ownership.
public struct AgendaRow<Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let timeLabel: String
    private let detail: String?
    private let status: Status
    private let actions: Actions
    public init(_ title: String, timeLabel: String, detail: String? = nil,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.timeLabel = timeLabel; self.detail = detail; self.status = status(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            VStack(alignment: .leading, spacing: t.space.inline) {
                Text(timeLabel).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                Text(title).font(t.typography.label)
                if let detail { Text(detail).font(t.typography.body).foregroundStyle(t.colors.inkSecondary.color) }
            }.accessibilityElement(children: .combine)
            status
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(t.colors.ink.color)
    }
}
extension AgendaRow where Status == EmptyView, Actions == EmptyView {
    public init(_ title: String, timeLabel: String, detail: String? = nil) {
        self.init(title, timeLabel: timeLabel, detail: detail, status: { EmptyView() }, actions: { EmptyView() })
    }
}

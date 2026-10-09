import SwiftUI

/// Passive device/session copy and independent status/actions. Caller owns current-session and revocation policy.
public struct SessionRow<Icon: View, Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let detail: String?
    private let activityLabel: String
    private let icon: Icon
    private let status: Status
    private let actions: Actions
    public init(_ title: String, activityLabel: String, detail: String? = nil, @ViewBuilder icon: () -> Icon,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.activityLabel = activityLabel; self.detail = detail
        self.icon = icon(); self.status = status(); self.actions = actions()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            ListRow(title, subtitle: detail, leading: { icon.accessibilityHidden(true) }, trailing: { EmptyView() })
            Text(activityLabel).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                .fixedSize(horizontal: false, vertical: true)
            status
            actions
        }.frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(t.colors.ink.color)
    }
}

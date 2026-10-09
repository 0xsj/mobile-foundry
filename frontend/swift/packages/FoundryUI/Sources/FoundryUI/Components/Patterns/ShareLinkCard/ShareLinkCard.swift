import SwiftUI

/// Supplied selectable link or unavailable copy, plus independent status/actions. No URL creation, copying or authorization.
public struct ShareLinkCard<Status: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let link: String?
    private let unavailableLabel: String
    private let detail: String?
    private let status: Status
    private let actions: Actions
    public init(_ title: String, link: String?, unavailableLabel: String, detail: String? = nil,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.link = link; self.unavailableLabel = unavailableLabel; self.detail = detail
        self.status = status(); self.actions = actions()
    }
    public var body: some View {
        Card {
            Text(title).font(t.typography.heading).accessibilityAddTraits(.isHeader)
            if let link {
                Text(link).font(t.typography.code).textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
            } else { Text(unavailableLabel).font(t.typography.body).foregroundStyle(t.colors.inkSecondary.color) }
            if let detail { Text(detail).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
            status
            actions
        }
    }
}

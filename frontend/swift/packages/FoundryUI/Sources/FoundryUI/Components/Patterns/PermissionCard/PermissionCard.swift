import SwiftUI

/// Rationale, supplied status and independent caller actions. Never queries permissions or opens settings.
public struct PermissionCard<Icon: View, Status: View, Actions: View>: View {
    private let title: String
    private let message: String
    private let icon: Icon
    private let status: Status
    private let actions: Actions
    public init(_ title: String, message: String, @ViewBuilder icon: () -> Icon,
                @ViewBuilder status: () -> Status, @ViewBuilder actions: () -> Actions) {
        self.title = title; self.message = message; self.icon = icon(); self.status = status(); self.actions = actions()
    }
    public var body: some View {
        Card {
            icon.accessibilityHidden(true)
            SectionHeader(title, subtitle: message)
            status
            actions
        }
    }
}
